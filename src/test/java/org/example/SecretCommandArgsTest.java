package org.example;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IllegalFormatConversionException;
import java.util.MissingFormatArgumentException;

import static org.junit.jupiter.api.Assertions.*;

class SecretCommandArgsTest {

    @Nested
    class ArgumentsTest {

        @Test
        public void toMaskedString() throws Exception {
            Class<?> innerClass = Class.forName("org.example.SecretCommandArgs$Argument");
            Constructor<?> constructor = innerClass.getDeclaredConstructor(String.class, String[].class);
            constructor.setAccessible(true);

            Object instance = constructor.newInstance("%s:%s", new String[]{"foo", "bar"});

            Method m = instance.getClass().getDeclaredMethod("toMaskedString", String.class);
            m.setAccessible(true);

            assertEquals("<secret>:<secret>", m.invoke(instance, "<secret>"));
            assertEquals(":", m.invoke(instance, ""));

            // 引数にnullを指定した場合、AssertionErrorになること
            // （ただしMethod.invoke()からはInvocationTargetExceptionにラップされてスローされる）
            Exception e = assertThrows(InvocationTargetException.class, () -> m.invoke(instance, (String)null));
            assertEquals(AssertionError.class, e.getCause().getClass());
        }

        @Test
        public void toRawString() throws Exception {
            Class<?> innerClass = Class.forName("org.example.SecretCommandArgs$Argument");
            Constructor<?> constructor = innerClass.getDeclaredConstructor(String.class, String[].class);
            constructor.setAccessible(true);

            Object instance = constructor.newInstance("%s:%s", new String[]{"foo", "bar"});

            Method m = instance.getClass().getDeclaredMethod("toRawString");
            m.setAccessible(true);
            assertEquals("foo:bar", m.invoke(instance));
        }
    }

    @Test
    public void toArgs() {
        SecretCommandArgs cmd= SecretCommandArgs.builder()
            .add("command")
            .add("--username")
            .add("john smith")
            .add("--password")
            .add("%s", "secret")
            .build();

        assertArrayEquals(new String[]{"command", "--username", "john smith", "--password", "secret"}, cmd.toArgs());

        // alt()やmask()は影響しないこと
        assertArrayEquals(cmd.toArgs(), cmd.alt("********").toArgs());
        assertArrayEquals(cmd.toArgs(), cmd.mask("********").toArgs());

        // 戻り値を書き換えても、CommandLineArgsの内部には影響しないこと
        String[] args = cmd.toArgs();
        args[0] = "xxx";
        assertArrayEquals(new String[]{"command", "--username", "john smith", "--password", "secret"}, cmd.toArgs());
    }

    @Test
    public void toArgs_IncludesSpaceAndQuote() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("command")
            .add("--username")
            .add("john smith")
            .add("--password")
            .add("%s", "P@ss\"w0rd")
            .build();

        // 空白文字や'"'を含んでいてもtoArgs()の場合はクォートされないこと
        assertArrayEquals(new String[]{"command", "--username", "john smith", "--password", "P@ss\"w0rd"}, cmd.toArgs());
    }

    @Test
    public void toRawSecretString() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("passwd")
            .add("%s", "P@ssw0rd")
            .build();

        assertEquals("passwd P@ssw0rd", cmd.toRawSecretString());
    }
    @Test
    public void toRawSecret_IncludesSpace() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("passwd")
            .add("%s", "P@ss w0rd")
            .build();

        assertEquals("passwd \"P@ss w0rd\"", cmd.toRawSecretString());
    }
    @Test
    public void toRawSecret_IncludesDoubleQuote() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("passwd")
            .add("%s", "P@ss\"w0rd")
            .build();

        assertEquals("passwd \"P@ss\\\"w0rd\"", cmd.toRawSecretString());
    }

    @Test
    public void toString_WithSecret() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("command")
            .add("foo")
            .add("--credential")
            .add("%s:%s", "john", "secret")
            .build();

        assertEquals("command foo --credential ****:****", cmd.toString());
    }

    @Test
    public void toString_IncludesSpace() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("command")
            .add("foo bar")
            .add("--credential")
            .add("%s:%s", "john", "P@ss w0rd")
            .build();

        assertEquals("command \"foo bar\" --credential ****:****", cmd.toString());
    }

    @Test
    public void toString_IncludesDoubleQuote() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("command")
            .add("foo\"bar")
            .add("--credential")
            .add("%s:%s", "john", "P@ss\"w0rd")
            .build();

        assertEquals("command \"foo\\\"bar\" --credential ****:****", cmd.toString());
    }

    @Test
    public void alt() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("openssl")
            .add("enc")
            .add("-pass")
            .add("pass:%s", "P@ssw0rd")
            .build()
            .alt("alternative text");

        assertArrayEquals(new String[]{"openssl", "enc", "-pass", "pass:P@ssw0rd"}, cmd.alt("alternative text").toArgs());
        assertEquals("alternative text", cmd.alt("alternative text").toString());

        assertArrayEquals(new String[]{"openssl", "enc", "-pass", "pass:P@ssw0rd"}, cmd.alt(null).toArgs());
        assertEquals("openssl enc -pass pass:****", cmd.alt(null).toString());

        assertArrayEquals(new String[]{"openssl", "enc", "-pass", "pass:P@ssw0rd"}, cmd.alt("alternative text").toArgs());
        assertEquals("alternative text", cmd.alt("alternative text").toString());
    }

    @Test
    public void mask() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("openssl")
            .add("enc")
            .add("-pass")
            .add("pass:%s", "P@ssw0rd")
            .build();

        assertEquals("openssl enc -pass pass:****", cmd.toString());

        assertEquals("openssl enc -pass pass:", cmd.mask("").toString());

        assertEquals("openssl enc -pass pass:<password>", cmd.mask("<password>").toString());

        // mask()しても元のオブジェクトには影響が無いこと
        assertEquals("openssl enc -pass pass:****", cmd.toString());

        // マスク文字列にはnullを指定できないこと
        assertThrows(IllegalArgumentException.class, () -> cmd.mask(null));

        // マスク文字列には空白文字やダブルクォートは含められないこと
        assertThrows(IllegalArgumentException.class, () -> cmd.mask(" "));
        assertThrows(IllegalArgumentException.class, () -> cmd.mask("* *"));
        assertThrows(IllegalArgumentException.class, () -> cmd.mask("\""));
        assertThrows(IllegalArgumentException.class, () -> cmd.mask("*\"*"));
    }

    @Test
    public void builder() {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("command")
            .add("%s", "secret1")
            .addSecret("secret2")
            .build();

        assertArrayEquals(new String[]{"command", "secret1", "secret2"}, cmd.toArgs());
        assertEquals("command **** ****", cmd.toString());
    }

    @Test
    public void builderEmpty() {
        SecretCommandArgs cmd = SecretCommandArgs.builder().build();

        assertArrayEquals(new String[0], cmd.toArgs());
        assertEquals("", cmd.toString());
    }

    @Test
    public void builderIllegalArgument() {
        // "%d"ではStringのフォーマットが出来ない
        assertThrows(IllegalFormatConversionException.class, () -> SecretCommandArgs.builder().add("%d", "123"));

        // プレイスホルダー"%s"の方が多い場合はエラー（String.format()の仕様）
        assertThrows(MissingFormatArgumentException.class, () -> SecretCommandArgs.builder().add("%s"));
        assertThrows(MissingFormatArgumentException.class, () -> SecretCommandArgs.builder().add("%s%s", ""));

        // プレイスホルダー"%s"の方が少ない場合はエラーにはならない（String.format()の仕様）
        SecretCommandArgs cmd = SecretCommandArgs.builder().add("%s:%s", "alpha", "bravo", "charlie").build();
        assertArrayEquals(new String[]{"alpha:bravo"}, cmd.toArgs());
    }

    @Test
    public void quoteIfNeeded() throws Exception {
        Method m = SecretCommandArgs.class.getDeclaredMethod("quoteIfNeeded", String.class);
        m.setAccessible(true);

        // nullを渡した場合はNullPointerExceptionがスローされること
        // （ただしMethod.invoke()からはInvocationTargetExceptionにラップされてスローされる）
        Exception e = assertThrows(InvocationTargetException.class, () -> m.invoke(null, (String) null));
        assertEquals(NullPointerException.class, e.getCause().getClass());

        assertEquals("", m.invoke(null, ""));
        assertEquals("abc", m.invoke(null, "abc"));

        // 空白文字を含む場合
        assertEquals("\" \"", m.invoke(null, " "));
        assertEquals("\"\t\"", m.invoke(null, "\t"));
        assertEquals("\"\n\"", m.invoke(null, "\n"));
        assertEquals("\"abc \"", m.invoke(null, "abc "));
        assertEquals("\" xyz\"", m.invoke(null, " xyz"));
        assertEquals("\"foo bar\"", m.invoke(null, "foo bar"));

        // ダブルクォートを含む場合
        assertEquals("\"\\\"\"", m.invoke(null, "\""));
        assertEquals("\"abc\\\"xyz\"", m.invoke(null, "abc\"xyz"));
    }
}
