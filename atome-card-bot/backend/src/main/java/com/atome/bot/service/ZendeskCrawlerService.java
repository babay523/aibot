package com.atome.bot.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ZendeskCrawlerService {

    private static final int TIMEOUT_MS = 30000;
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Cache-Control", "max-age=0");
        headers.put("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");
        headers.put("Sec-Ch-Ua-Mobile", "?0");
        headers.put("Sec-Ch-Ua-Platform", "\"macOS\"");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("DNT", "1");
        return headers;
    }

    /**
     * 抓取分类页，获取所有文章链接
     */
    public Set<String> extractArticleUrls(String categoryUrl) throws IOException {
        Set<String> articleUrls = new HashSet<>();
        
        var connection = Jsoup.connect(categoryUrl)
                .timeout(TIMEOUT_MS)
                .userAgent(USER_AGENT)
                .followRedirects(true)
                .ignoreHttpErrors(false);
        
        // 添加所有默认请求头
        getDefaultHeaders().forEach(connection::header);
        
        Document doc = connection.get();

        // Zendesk 文章链接格式：/hc/en-gb/articles/123456789-Title
        Elements articleLinks = doc.select("a[href*=/hc/en-gb/articles/]");
        for (Element link : articleLinks) {
            String href = link.attr("href");
            String absoluteUrl = resolveUrl(categoryUrl, href);
            if (absoluteUrl != null) {
                articleUrls.add(absoluteUrl);
            }
        }

        return articleUrls;
    }

    /**
     * 抓取单篇文章，返回 title + url + bodyText
     */
    public ArticleContent fetchArticle(String articleUrl) throws IOException {
        var connection = Jsoup.connect(articleUrl)
                .timeout(TIMEOUT_MS)
                .userAgent(USER_AGENT)
                .followRedirects(true);
        
        // 添加所有默认请求头
        getDefaultHeaders().forEach(connection::header);
        connection.header("Referer", "https://help.atome.ph/");
        
        Document doc = connection.get();

        // 提取标题
        String title = extractTitle(doc);

        // 提取正文（优先 article 标签或主要内容区）
        String bodyText = extractBodyText(doc);

        // 优先使用 canonical URL
        String canonicalUrl = extractCanonicalUrl(doc);
        if (canonicalUrl != null && !canonicalUrl.isEmpty()) {
            articleUrl = canonicalUrl;
        }

        return new ArticleContent(title, articleUrl, bodyText);
    }

    private String extractTitle(Document doc) {
        // 优先 h1，然后是 title
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isEmpty()) {
            return h1.text().trim();
        }
        String title = doc.title();
        return title != null ? title.trim() : "";
    }

    private String extractBodyText(Document doc) {
        // 尝试多种选择器找到文章正文
        Element article = doc.selectFirst("article");
        if (article == null) {
            article = doc.selectFirst(".article-body");
        }
        if (article == null) {
            article = doc.selectFirst("[itemprop=articleBody]");
        }
        if (article == null) {
            article = doc.selectFirst(".content");
        }

        if (article == null) {
            // 如果都找不到，用 body 但要去掉导航等
            article = doc.body();
        }

        // 清理：移除固定块、导航、页脚等
        article.select("nav, header, footer, .sidebar, .navigation, script, style").remove();
        article.select("[class*=contact], [class*=footer], [id*=footer]").remove();

        // 获取纯文本，保留段落结构
        String text = article.text();
        
        // 清理多余空白
        return text.replaceAll("\\s+", " ").trim();
    }

    private String extractCanonicalUrl(Document doc) {
        Element canonical = doc.selectFirst("link[rel=canonical]");
        if (canonical != null) {
            String href = canonical.attr("href");
            if (!href.isEmpty()) {
                return href;
            }
        }
        return null;
    }

    private String resolveUrl(String baseUrl, String href) {
        if (href == null || href.isEmpty()) return null;
        
        try {
            URI baseUri = new URI(baseUrl);
            URI resolved = baseUri.resolve(href);
            return resolved.toString();
        } catch (URISyntaxException e) {
            // 简单拼接
            if (href.startsWith("http")) {
                return href;
            } else if (href.startsWith("/")) {
                try {
                    URI base = new URI(baseUrl);
                    return base.getScheme() + "://" + base.getHost() + href;
                } catch (URISyntaxException ex) {
                    return null;
                }
            }
            return null;
        }
    }

    public record ArticleContent(String title, String url, String bodyText) {}
}