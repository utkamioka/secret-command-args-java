package org.example;

import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SecretCommandArgsクラスは表示すべきでは無いシークレットを含むコマンドライン引数を表します。
 * <p>
 * シークレットをそのまま含む実行用の文字列配列の生成と、
 * シークレットをマスクした表示用の文字列の生成を行うことが出来ます。
 * <pre>
 * {@code
 * SecretCommandArgs cmd = SecretCommandArgs.builder()
 *     .add("passwd")
 *     .add("%s", "P@ssw0rd")
 *     .build();
 *
 * process.invoke(cmd.toArgs());  // Execute ["passwd", "P@ssw0rd"]
 * logger.info("invoking '{}'", cmd);  // Output "invoking 'passwd ****'" to logger
 * }
 * </pre>
 *
 * このクラスは不変（Immutable）であり、
 * インスタンスが作成された後はその状態が変更されません。
 * <p>
 * この不変性は以下のようにして保証されています：
 * <ul>
 *   <li>すべてのフィールドはfinalであり、コンストラクタでのみ値が設定されます。</li>
 *   <li>クラス自体がfinalであり、サブクラス化による不変性の破壊を防止しています。</li>
 *   <li>外部からの変更を防ぐため、ミュータブルなオブジェクトへの参照を返す場合、
 *       ディープコピーを作成して返します。</li>
 * </ul>
 */
public final class SecretCommandArgs {

    private static class Argument {
        private final String arg;
        private final String[] secrets;

        private Argument(String arg, String... secrets) {
            _verify(arg, secrets);
            this.arg = arg;
            this.secrets = secrets;
        }

        private static void _verify(String format, String... args) throws IllegalFormatException {
            //noinspection ResultOfMethodCallIgnored
            String.format(format, (Object[]) args);
        }

        private String toMaskedString(String mask) {
            assert mask != null;
            Object[] masked = Arrays.stream(this.secrets).map(s -> mask).toArray(Object[]::new);
            return String.format(this.arg, masked);
        }

        private String toRawString() {
            return String.format(this.arg, (Object[]) this.secrets);
        }
    }

    private final Argument[] args;
    private final String alt;
    private final String mask;

    private SecretCommandArgs(Argument... args) {
        this(args, null, "****");
    }

    private SecretCommandArgs(Argument[] args, String alt, String mask) {
        if (mask == null || mask.matches(".*[\\s\"].*")) {
            throw new IllegalArgumentException();
        }
        this.args = args;
        this.alt = alt;
        this.mask = mask;
    }

    /**
     * 指定された代替テキストを適用した新しい SecretCommandArgs インスタンスを作成します。
     * 代替テキストは{@link #toString()}で生成される文字列として使用されます。
     * <pre>
     * {@code
     * SecretCommandArgs cmd = SecretCommandArgs.builder()
     *     .add("passwd")
     *     .add("%s", "P@ssw0rd")
     *     .build();
     *
     * System.out.println(cmd.toString());  // "passwd ****"
     * System.out.println(cmd.alt("パスワードを変更します").toString());  // "パスワードを変更します"
     * }
     * </pre>
     *
     * @param text SecretCommandArgs オブジェクトに適用される代替テキスト
     * @return 指定された代替テキストを持った新しい SecretCommandArgs オブジェクト
     */
    public SecretCommandArgs alt(String text) {
        return new SecretCommandArgs(args, text, this.mask);
    }

    /**
     * シークレットをマスクするために使用するテキスト適用した新しい SecretCommandArgs インスタンスを作成します。
     * <pre>
     * {@code
     * SecretCommandArgs cmd = SecretCommandArgs.builder()
     *     .add("passwd")
     *     .add("%s", "P@ssw0rd")
     *     .build();
     * System.out.println(cmd.toString());  // "passwd ****"
     * System.out.println(cmd.mask("<password>").toString());  // "passwd <password>"
     * }
     * </pre>
     *
     * @param text マスクに使用するテキスト
     * @return 指定されたマスク用テキストを持つ新しい SecretCommandArgs オブジェクト
     * @throws IllegalArgumentException 指定されたテキストがnull、または空白文字やダブルクォートを含む場合
     */
    public SecretCommandArgs mask(String text) {
        return new SecretCommandArgs(args, this.alt, text);
    }

    /**
     * 生のシークレットを含むコマンドライン引数を取得します。
     * <p>
     * 一つのStringでコマンド文字列全体を表現するために
     * このメソッドの結果を{@link String#join}で連結するのは、
     * 必要なエスケープ処理が不足しているため間違ったアプローチです。
     * {@link #toRawSecretString()}を使ってください。
     * 間違った例:
     * <pre>
     * {@code
     * SecretCommandArgs cmd= SecretCommandArgs.builder()
     *     .add("execute")
     *     .add("--username")
     *     .add("john smith")
     *     .add("command.sh")
     *     .build();
     * System.out.println(String.join(" ", cmd.toArgs()));
     * // "execute --username john smith command.sh"  # --usernameオプションへ指定した引数が分割されてしまっている
     * }
     * </pre>
     *
     * @return シークレットを含む文字列配列
     */
    public String[] toArgs() {
        return Arrays.stream(args)
            .map(Argument::toRawString)
            .toArray(String[]::new);
    }

    /**
     * 生のシークレットを格納したコマンド文字列を生成します。
     * <pre>
     * {@code
     * SecretCommandArgs cmd= SecretCommandArgs.builder()
     *     .add("execute")
     *     .add("--username")
     *     .add("john smith")
     *     .add("command.sh")
     *     .build();
     * System.out.println(cmd.toRawSecretString());
     * // => "execute --username \"john smith\" command.sh"
     * }
     * </pre>
     */
    public String toRawSecretString() {
        return Arrays.stream(args)
            .map(Argument::toRawString)
            .map(SecretCommandArgs::quoteIfNeeded)
            .collect(Collectors.joining(" "));
    }

    private String toMaskedString() {
        if (alt != null) {
            return alt;
        }
        return Arrays.stream(args)
            .map(arg -> arg.toMaskedString(mask))
            .map(SecretCommandArgs::quoteIfNeeded)
            .collect(Collectors.joining(" "));
    }

    /**
     * シークレットをマスクしたコマンド文字列を生成します。
     */
    @Override
    public String toString() {
        return this.toMaskedString();
    }

    /**
     * 必要であれば文字列をダブルクォートで囲む。
     * 空白文字、またはダブルクォートを含んでいる場合には必要であると判断する。
     * また、ダブルクォートはバックスラッシュでエスケープする。
     */
    private static String quoteIfNeeded(String s) {
        if (!s.matches(".*[\"\\s].*")) return s;
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    /**
     * SecretCommandArgs オブジェクトを生成するためのビルダーインスタンスを取得します。
     * <pre>
     * {@code
     * SecretCommandArgs cmd = SecretCommandArgs.builder()
     *     .add("password")
     *     .add("%s", "P@ssw0rd")
     *     .build();
     * }
     * </pre>
     *
     * @return ビルダーインスタンス
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Argument> args = new LinkedList<>();

        private Builder() {}

        /**
         * SecretCommandArgs オブジェクトに追加するコマンド引数を指定します。
         * <pre>
         * {@code
         * SecretCommandArgs cmd = SecretCommandArgs.builder()
         *     .add("openssl")
         *     .add("enc")
         *     .add("-pass")
         *     .add("pass:%s", "P@ssw0rd")
         *     .build();
         * }
         * </pre>
         * シークレットを含む場合は該当部分にプレースホルダー文字列("%s")を記述してください。
         * シークレットでは無く"%s"という引数を追加したい場合は"%%S"としてください。
         * なお、プレースホルダー"%s"は{@link String#format}の「書式文字列」の仕様に基づくものです。
         *
         * @see String#format
         *
         * @param arg シークレット用プレースホルダー("%s")を含むコマンド引数
         * @param secrets プレースホルダー("%s")に対応する個数のシークレット
         * @return ビルダーインスタンス
         */
        public Builder add(String arg, String... secrets) {
            this.args.add(new Argument(arg, secrets));
            return this;
        }

        /**
         * {@code add("%s", secret)}の短縮インターフェイスです。
         * @param secret シークレット
         * @return ビルダーインスタンス
         * @see #add(String, String...)
         */
        public Builder addSecret(String secret) {
            add("%s", secret);
            return this;
        }

        /**
         * SecretCommandArgs インスタンスを生成する。
         *
         * @return 新しい SecretCommandArgs インスタンス
         */
        public SecretCommandArgs build() {
            return new SecretCommandArgs(args.toArray(new Argument[0]));
        }
    }
}
