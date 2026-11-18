package util;

import java.util.*;

public class Json {
    public static String text(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char letter : value.toCharArray()) {
            switch (letter) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (letter < 32) result.append(String.format("\\u%04x", (int) letter));
                    else result.append(letter);
            }
        }
        return result.append('"').toString();
    }

    public static String array(List<String> rows) {
        return "[" + String.join(",", rows) + "]";
    }
}
