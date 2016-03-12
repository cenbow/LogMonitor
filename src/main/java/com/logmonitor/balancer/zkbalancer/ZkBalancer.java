package com.logmonitor.balancer.zkbalancer;

import com.logmonitor.balancer.Configuration;
import com.logmonitor.balancer.node.SourceNode;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.zookeeper.CreateMode;

import java.lang.reflect.Field;

/**
 * Created by wanghaiyang on 16/3/12.
 */
public class ZkBalancer {
    protected CuratorFramework client;
    protected String parentPath = "/";
    protected CreateMode nodeMode = CreateMode.PERSISTENT;
    protected String zkSourceParentPath = "";
    protected String zkConsumeParentPath = "";

    public ZkBalancer(String connectString, int sessionTimeoutMs, int connectTimeoutMs, RetryPolicy retryPolicy) {
        client = CuratorFrameworkFactory.newClient(connectString, sessionTimeoutMs, connectTimeoutMs, retryPolicy);
        client.start();
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public void setZkSourceParentPath(String zkSourceParentPath) {
        this.zkSourceParentPath = zkSourceParentPath;
    }

    public void setZkConsumeParentPath(String zkConsumeParentPath) {
        this.zkConsumeParentPath = zkConsumeParentPath;
    }

    public void setNodeMode(Configuration.ZkCreateMode nodeMode) {
        switch (nodeMode) {
            case PERSISTENT:
                this.nodeMode = CreateMode.PERSISTENT;
                break;
            case EPHEMERAL:
                this.nodeMode = CreateMode.EPHEMERAL;
                break;
            default:
                throw new RuntimeException("Illegal Zk Create Mode.");
        }
    }

    public void createNode(String path, byte[] data) {
        try {
            client.create().creatingParentsIfNeeded().withMode(nodeMode)
                    .forPath(path,data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create node: " + path, e);
        }
    }

    public void createNode(String path) {
        try {
            client.create().creatingParentsIfNeeded().withMode(nodeMode)
                    .forPath(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create node: " + path, e);
        }
    }

    public void createByNodeObj(Object node, String path) {
        Field[] fields = node.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().startsWith("_")) {
                try {
                    field.setAccessible(true);
                    createNode(path + "/" + field.getName().substring(1), field.get(node).toString().getBytes());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void getByNodeObj(Object node, String path) {
        Field[] fields = node.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().startsWith("_")) {
                try {
                    field.setAccessible(true);
                    String nodePath = path + "/" + field.getName().substring(1);
                    byte[] data = client.getData().forPath(nodePath);
                    if (field.getType().equals(int.class)) {
                        field.setInt(node, Integer.valueOf(new String(data)));
                    } else if (field.getType().equals(boolean.class)) {
                        field.setBoolean(node, Boolean.valueOf(new String(data)));
                    } else if (field.getType().equals(String.class)) {
                        field.set(node, new String(data));
                    } else if (field.getType().equals(SourceNode.DIRECTION.class)) {
                        String tmp = new String(data);
                        if (tmp.equals(SourceNode.DIRECTION.LPOP)) {
                            field.set(node, SourceNode.DIRECTION.LPOP);
                        } else if (tmp.equals(SourceNode.DIRECTION.RPOP)) {
                            field.set(node, SourceNode.DIRECTION.RPOP);
                        }
                    } else {
                        field.set(node, data);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public boolean deleteNode(String path) {
        try {
            client.delete().guaranteed().deletingChildrenIfNeeded().forPath(path);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public void close() {
        client.close();
    }
}