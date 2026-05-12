package com.cheddarflow.collector.ingestion.parsing;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OptionsFlowRowParser {
    private static final List<String> COLUMN_IDS = List.of(
            "time",
            "date",
            "tick",
            "expiry",
            "strike",
            "putCall",
            "side",
            "buySell",
            "spot",
            "size",
            "price",
            "premium",
            "sweepBlockSplit",
            "volume",
            "openInt",
            "conds"
    );
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<\\s*(/)?\\s*([a-zA-Z0-9:-]+)([^>]*)>");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("(?is)<li\\b[^>]*>(.*?)</li>");
    private static final Pattern BUTTON_PATTERN = Pattern.compile("(?is)<button\\b[^>]*>(.*?)</button>");
    private static final Map<String, BigDecimal> PREMIUM_SUFFIXES = Map.of(
            "K", BigDecimal.valueOf(1_000L),
            "M", BigDecimal.valueOf(1_000_000L),
            "B", BigDecimal.valueOf(1_000_000_000L)
    );

    public ParsedOptionsFlowRow parse(String sourceHtml) {
        Map<String, String> extracted = new LinkedHashMap<>();
        for (String columnId : COLUMN_IDS) {
            String cellHtml = findCellInnerHtml(sourceHtml, columnId);
            extracted.put(columnId, extractCellText(cellHtml));
        }

        String symbol = extractCellText(findCellInnerHtml(sourceHtml, "symbol"));
        if (symbol.isBlank()) {
            symbol = extracted.get("tick");
        }

        String premiumText = extracted.get("premium");
        return new ParsedOptionsFlowRow(
                extracted.get("time"),
                extracted.get("date"),
                symbol,
                extracted.get("expiry"),
                extracted.get("strike"),
                extracted.get("putCall"),
                extracted.get("side"),
                extracted.get("buySell"),
                extracted.get("spot"),
                extracted.get("size"),
                extracted.get("price"),
                premiumText,
                premiumText.isBlank() ? null : parseNumericWithSuffix(premiumText),
                extracted.get("sweepBlockSplit"),
                extracted.get("volume"),
                extracted.get("openInt"),
                extracted.get("conds")
        );
    }

    private BigDecimal parseNumericWithSuffix(String value) {
        String cleaned = collapseWhitespace(value).replace("$", "").replace(",", "");
        if (cleaned.isEmpty()) {
            return null;
        }

        BigDecimal multiplier = BigDecimal.ONE;
        String suffix = cleaned.substring(cleaned.length() - 1).toUpperCase(Locale.ROOT);
        if (PREMIUM_SUFFIXES.containsKey(suffix)) {
            multiplier = PREMIUM_SUFFIXES.get(suffix);
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        return new BigDecimal(cleaned).multiply(multiplier);
    }

    private String findCellInnerHtml(String sourceHtml, String columnId) {
        String attributeNeedleDoubleQuote = "col-id=\"" + columnId + "\"";
        String attributeNeedleSingleQuote = "col-id='" + columnId + "'";

        int attributeIndex = sourceHtml.indexOf(attributeNeedleDoubleQuote);
        if (attributeIndex < 0) {
            attributeIndex = sourceHtml.indexOf(attributeNeedleSingleQuote);
        }
        if (attributeIndex < 0) {
            return "";
        }

        int tagStart = sourceHtml.lastIndexOf('<', attributeIndex);
        if (tagStart < 0) {
            return "";
        }

        Matcher openingTagMatcher = TAG_PATTERN.matcher(sourceHtml);
        openingTagMatcher.region(tagStart, sourceHtml.length());
        if (!openingTagMatcher.lookingAt()) {
            return "";
        }

        String tagName = openingTagMatcher.group(2).toLowerCase(Locale.ROOT);
        int contentStart = openingTagMatcher.end();
        int depth = 1;

        Matcher nestedTagMatcher = TAG_PATTERN.matcher(sourceHtml);
        nestedTagMatcher.region(contentStart, sourceHtml.length());
        while (nestedTagMatcher.find()) {
            String nestedTagName = nestedTagMatcher.group(2).toLowerCase(Locale.ROOT);
            if (!nestedTagName.equals(tagName)) {
                continue;
            }

            boolean closing = nestedTagMatcher.group(1) != null;
            boolean selfClosing = !closing && nestedTagMatcher.group(3) != null
                    && nestedTagMatcher.group(3).stripTrailing().endsWith("/");

            if (closing) {
                depth -= 1;
                if (depth == 0) {
                    return sourceHtml.substring(contentStart, nestedTagMatcher.start());
                }
                continue;
            }

            if (!selfClosing) {
                depth += 1;
            }
        }

        return sourceHtml.substring(contentStart);
    }

    private String extractCellText(String cellHtml) {
        if (cellHtml == null || cellHtml.isBlank()) {
            return "";
        }

        List<String> listItems = extractTagTexts(cellHtml, LIST_ITEM_PATTERN);
        if (!listItems.isEmpty()) {
            return String.join(" ", listItems);
        }

        List<String> buttons = extractTagTexts(cellHtml, BUTTON_PATTERN);
        if (!buttons.isEmpty()) {
            return buttons.get(0);
        }

        return stripTags(cellHtml);
    }

    private List<String> extractTagTexts(String cellHtml, Pattern pattern) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(cellHtml);
        while (matcher.find()) {
            String normalized = stripTags(matcher.group(1));
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private String stripTags(String html) {
        String withSpaces = html
                .replaceAll("(?is)<br\\s*/?>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        return collapseWhitespace(HtmlUtils.htmlUnescape(withSpaces));
    }

    private String collapseWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
