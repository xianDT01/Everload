package com.everload.everload.service;

import java.util.OptionalInt;

final class YtDlpProgressParser {

    private YtDlpProgressParser() {
    }

    static OptionalInt parse(String line) {
        int percentIndex = line.indexOf('%');
        if (percentIndex <= 0) return OptionalInt.empty();

        int start = percentIndex - 1;
        boolean decimalPointFound = false;
        while (start >= 0) {
            char current = line.charAt(start);
            if (Character.isDigit(current)) {
                start--;
            } else if (current == '.' && !decimalPointFound) {
                decimalPointFound = true;
                start--;
            } else {
                break;
            }
        }

        String value = line.substring(start + 1, percentIndex);
        if (value.isEmpty() || value.equals(".")) return OptionalInt.empty();
        try {
            return OptionalInt.of((int) Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }
}
