
package com.brian.codeblog.model;

/**
 * 博客实体类
 */
public class BlogInfo extends BaseType {
    private static final long serialVersionUID = 1L;
    
    public String blogId; // MD5 of Link
    public String title; // 标题
    public String link; // 文章链接
    public String dateStamp; // 博客发布时间
    public long visitTime; // 博客访问时间，用于排序
    public String summary;// 文章摘要
    public String localPath; // 文章缓存文件路径
    public String extraMsg; // 消息
    public int type; // 博客类型，在TypeManager中定义
    public String blogerID;

    public String blogerJson;

    public boolean isFavo = false;

    public String toJson() {
        return getGson().toJson(this);
    }

    public static BlogInfo fromJson(String blogJson) {
        return getGson().fromJson(blogJson, BlogInfo.class);
    }
}
