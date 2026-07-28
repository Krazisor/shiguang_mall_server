package org.dhu.shiguang_market.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContentSafety {
    private static final int MAX_IMAGE_URL_LENGTH = 1024;
    private static final int MAX_DETAIL_HTML_LENGTH = 1_000_000;
    private static final Safelist DETAIL_SAFELIST = new Safelist()
            .addTags("a", "b", "blockquote", "br", "caption", "cite", "code", "col", "colgroup",
                    "dd", "div", "dl", "dt", "em", "h1", "h2", "h3", "h4", "h5", "h6", "hr",
                    "i", "img", "li", "ol", "p", "pre", "q", "small", "span", "strong", "sub",
                    "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "u", "ul")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("ol", "start", "type")
            .addAttributes("table", "summary")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan", "scope")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    private final boolean allowLocalHttp;

    public ContentSafety(@Value("${market.content.allow-local-http:true}") boolean allowLocalHttp) {
        this.allowLocalHttp = allowLocalHttp;
    }

    public String imageUrl(String field, String rawValue) {
        String value = Formatters.trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        if (value.length() > MAX_IMAGE_URL_LENGTH) {
            throw invalid(field + " 长度不能超过 " + MAX_IMAGE_URL_LENGTH);
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException ex) {
            throw invalid(field + " 不是合法 URL");
        }
        if (uri.getRawUserInfo() != null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid(field + " 不是合法的绝对 URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return value;
        }
        if ("http".equals(scheme) && allowLocalHttp && isLocalHost(uri.getHost())) {
            return value;
        }
        throw invalid(field + " 只允许 HTTPS，本地开发仅允许配置开启的 localhost HTTP 地址");
    }

    public List<String> imageUrls(String field, List<String> rawValues, int maximum) {
        if (rawValues == null) {
            throw invalid(field + " 不能为空");
        }
        if (rawValues.size() > maximum) {
            throw invalid(field + " 最多允许 " + maximum + " 个 URL");
        }
        List<String> values = new ArrayList<>(rawValues.size());
        Set<String> unique = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            String value = imageUrl(field, rawValue);
            if (value == null) {
                throw invalid(field + " 不允许空 URL");
            }
            if (!unique.add(value)) {
                throw invalid(field + " 不允许重复 URL");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    public String detailHtml(String html) {
        if (html == null) {
            return null;
        }
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String cleaned = Jsoup.clean(html, "", DETAIL_SAFELIST, outputSettings);
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(outputSettings);
        for (Element image : document.select("img[src]")) {
            try {
                image.attr("src", imageUrl("detailHtml 图片地址", image.attr("src")));
            } catch (BusinessException ex) {
                image.removeAttr("src");
            }
        }
        String result = document.body().html();
        if (result.length() > MAX_DETAIL_HTML_LENGTH) {
            throw invalid("detailHtml 清洗后长度不能超过 " + MAX_DETAIL_HTML_LENGTH);
        }
        return result;
    }

    private boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }

    private BusinessException invalid(String message) {
        return BusinessException.badRequest("VALIDATION_FAILED", message);
    }
}
