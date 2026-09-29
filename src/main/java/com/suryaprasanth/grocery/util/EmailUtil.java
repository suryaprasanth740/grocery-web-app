package com.suryaprasanth.grocery.util;

import java.util.Locale;

/**
 * One Gmail inbox = one account.
 *
 * Gmail ignores dots and anything after a "+" in the name part, and googlemail.com is the
 * same as gmail.com. So all of these reach the SAME inbox:
 *   surya@gmail.com, s.u.r.y.a@gmail.com, surya+shop@gmail.com, SURYA@googlemail.com
 * Without this, one person could make many accounts (and reuse "first order" coupons).
 *
 * canonical() turns every form into one key ("surya@gmail.com"). Other providers are only
 * trimmed and lower-cased, because for them dots and "+" can be different mailboxes.
 */
public final class EmailUtil {

    private EmailUtil() {
    }

    public static String canonical(String email) {
        if (email == null) {
            return "";
        }
        String e = email.trim().toLowerCase(Locale.ROOT);
        int at = e.lastIndexOf('@');
        if (at <= 0) {
            return e;
        }
        String local = e.substring(0, at);
        String domain = e.substring(at + 1);
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            int plus = local.indexOf('+');
            if (plus >= 0) {
                local = local.substring(0, plus);
            }
            local = local.replace(".", "");
            domain = "gmail.com";
        }
        return local + "@" + domain;
    }

    /** False for things like "+shop@gmail.com" or "...@gmail.com" that have no real name part. */
    public static boolean hasName(String canonicalEmail) {
        return canonicalEmail.indexOf('@') > 0;
    }
}
