package org.bsl.meetingroom.security;

import java.security.SecureRandom;

/**
 * Shared password policy for generated/reset passwords.
 * Rule: minimum 12 characters, at least one uppercase, one lowercase,
 * one digit and one special character from @#$%&*!?, with no whitespace.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%&*!?])\\S{12,100}$";
    public static final String MESSAGE = "Password must be 12-100 characters and include uppercase, lowercase, number and special character (@#$%&*!?)";

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "@#$%&*!?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private PasswordPolicy() {}

    public static String generate(SecureRandom random) {
        return generate(random, MIN_LENGTH);
    }

    public static String generate(SecureRandom random, int length) {
        int safeLength = Math.max(length, MIN_LENGTH);
        char[] chars = new char[safeLength];
        chars[0] = pick(random, UPPER);
        chars[1] = pick(random, LOWER);
        chars[2] = pick(random, DIGITS);
        chars[3] = pick(random, SPECIAL);
        for (int i = 4; i < safeLength; i++) chars[i] = pick(random, ALL);
        shuffle(random, chars);
        return new String(chars);
    }

    public static boolean isValid(String password) {
        return password != null && password.matches(REGEX);
    }

    private static char pick(SecureRandom random, String source) {
        return source.charAt(random.nextInt(source.length()));
    }

    private static void shuffle(SecureRandom random, char[] chars) {
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
    }
}
