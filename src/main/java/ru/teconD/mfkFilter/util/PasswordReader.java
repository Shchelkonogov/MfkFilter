package ru.teconD.mfkFilter.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author Maksim Shchelkonogov
 */
public class PasswordReader {

    public String readLine(String fmt, Object... args) throws IOException {
        if (System.console() != null) {
            return System.console().readLine(fmt, args);
        }
        System.out.print(String.format(fmt, args));
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }

    public char[] readPassword(String fmt, Object... args) throws IOException {
        if (System.console() != null)
            return System.console().readPassword(fmt, args);
        return this.readLine(fmt, args).toCharArray();
    }
}
