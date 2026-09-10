package com.technnext.hrms.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a rupee amount to words using the Indian numbering system
 * (Thousand / Lakh / Crore), e.g. 85000 -> "Eighty Five Thousand".
 * Used on the payslip PDF's "Amount In Words" line.
 */
public final class AmountInWordsConverter {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private AmountInWordsConverter() {}

    public static String toWords(BigDecimal amount) {
        if (amount == null) return "";
        long rupees = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        if (rupees == 0) return "Indian Rupee Zero Only";
        return "Indian Rupee " + convert(rupees) + " Only";
    }

    private static String convert(long n) {
        if (n < 0) return "Minus " + convert(-n);
        StringBuilder sb = new StringBuilder();

        long crore = n / 10000000; n %= 10000000;
        long lakh = n / 100000; n %= 100000;
        long thousand = n / 1000; n %= 1000;
        long hundred = n / 100; n %= 100;

        if (crore > 0) sb.append(twoDigit((int) crore)).append(" Crore ");
        if (lakh > 0) sb.append(twoDigit((int) lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(twoDigit((int) thousand)).append(" Thousand ");
        if (hundred > 0) sb.append(ONES[(int) hundred]).append(" Hundred ");
        if (n > 0) {
            if (sb.length() > 0) sb.append("and ");
            sb.append(twoDigit((int) n));
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String twoDigit(int n) {
        if (n < 20) return ONES[n];
        return (TENS[n / 10] + " " + ONES[n % 10]).trim();
    }
}