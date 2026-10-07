package io.github.harshmittal.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Accepted;
import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Rejected;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** One table per rejection reason (SECURITY.md §4, S-01 to S-05), plus the accepted and normalized forms. */
class StandardUrlPolicyTest {

    private static final int MAX_LENGTH = 2048;

    private final StandardUrlPolicy policy = new StandardUrlPolicy("https://sho.rt");

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "JaVaScRiPt:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "data:text/html;base64,PHNjcmlwdD4=",
                "file:///etc/passwd",
                "ftp://example.com/file",
                "mailto:someone@example.com",
                "//example.com/no-scheme",
                "example.com/no-scheme"
            })
    @DisplayName("AC12, S-01: a scheme other than http or https is rejected with SCHEME_NOT_ALLOWED")
    void rejectsOtherSchemes(String target) {
        assertThat(policy.evaluate(target)).isEqualTo(new Rejected(UrlRejectionReason.SCHEME_NOT_ALLOWED));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "https://example.com/a?b=c#d, https://example.com/a?b=c#d",
        "http://example.com, http://example.com",
        "HTTPS://example.com/X, https://example.com/X",
        "' https://example.com/trimmed ', https://example.com/trimmed"
    })
    @DisplayName("S-01: http and https are accepted, compared case-insensitively after trimming")
    void acceptsHttpAndHttps(String target, String normalized) {
        assertThat(policy.evaluate(target)).isEqualTo(new Accepted(normalized));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                // localhost and its variants
                "http://localhost",
                "http://LOCALHOST:8080/x",
                "http://localhost./",
                "http://api.localhost/",
                "http://localhost.localdomain/",
                "http://ｌｏｃａｌｈｏｓｔ/", // fullwidth letters fold to localhost
                // loopback 127.0.0.0/8 in every encoding
                "http://127.0.0.1/",
                "http://127.255.255.254/",
                "http://127.0.0.1./",
                "http://127.1/",
                "http://127.0.1/",
                "http://2130706433/",
                "http://0x7f000001/",
                "http://0X7F.0.0.1/",
                "http://0177.0.0.1/",
                "http://017700000001/",
                "http://0x7f.1/",
                "http://１２７.０.０.１/", // fullwidth digits
                "http://127。0。0。1/", // ideographic full stops
                // private 10.0.0.0/8
                "http://10.0.0.1/",
                "http://10.255.255.255/",
                "http://012.0.0.1/",
                "http://0xa.0.0.1/",
                "http://167772161/",
                "http://10.1/",
                // private 172.16.0.0/12
                "http://172.16.0.1/",
                "http://172.31.255.255/",
                "http://0xac.0x10.0.1/",
                // private 192.168.0.0/16
                "http://192.168.1.1/",
                "http://3232235777/",
                "http://192.168.257/",
                // link-local 169.254.0.0/16, including cloud metadata
                "http://169.254.169.254/latest/meta-data/",
                "http://169.254.0.1/",
                "http://0xa9fea9fe/",
                // CGNAT 100.64.0.0/10
                "http://100.64.0.1/",
                "http://100.127.255.255/",
                // unspecified 0.0.0.0/8
                "http://0.0.0.0/",
                "http://0/",
                "http://0x0/",
                "http://0x/",
                "http://0.1.2.3/",
                // benchmarking 198.18.0.0/15
                "http://198.18.0.1/",
                "http://198.19.255.255/",
                // multicast 224.0.0.0/4
                "http://224.0.0.1/",
                "http://239.255.255.255/",
                "http://0xe0000001/",
                // limited broadcast
                "http://255.255.255.255/",
                "http://4294967295/",
                // IPv6 loopback, unspecified, unique-local, link-local
                "http://[::1]/",
                "http://[::1]:8080/",
                "http://[0:0:0:0:0:0:0:1]/",
                "http://[::]/",
                "http://[fc00::1]/",
                "http://[fd12:3456:789a::1]/",
                "http://[fe80::1]/",
                "http://[FE80::abcd]/",
                "http://[febf:ffff::1]/",
                // IPv4-mapped and IPv4-compatible IPv6 carrying a blocked IPv4
                "http://[::ffff:127.0.0.1]/",
                "http://[::ffff:7f00:1]/",
                "http://[::ffff:10.0.0.1]/",
                "http://[::ffff:169.254.169.254]/",
                "http://[::127.0.0.1]/",
                // site-local fec0::/10 and multicast ff00::/8
                "http://[fec0::1]/",
                "http://[feff:ffff::1]/",
                "http://[ff02::1]/",
                "http://[FF05::2]/",
                // NAT64 64:ff9b::/96 and 6to4 2002::/16 carrying a blocked IPv4
                "http://[64:ff9b::10.0.0.1]/",
                "http://[64:ff9b::7f00:1]/",
                "http://[2002:0a00:0001::]/",
                "http://[2002:7f00:1::1]/",
                "http://[2002:a9fe:a9fe::]/"
            })
    @DisplayName(
            "AC13, S-02: localhost and literal IPs in blocked ranges, in any encoding, are rejected with HOST_NOT_ALLOWED")
    void rejectsBlockedHosts(String target) {
        assertThat(policy.evaluate(target)).isEqualTo(new Rejected(UrlRejectionReason.HOST_NOT_ALLOWED));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        // just outside each blocked IPv4 range
        "http://9.255.255.255/, http://9.255.255.255/",
        "http://11.0.0.0/, http://11.0.0.0/",
        "http://126.255.255.255/, http://126.255.255.255/",
        "http://128.0.0.0/, http://128.0.0.0/",
        "http://172.15.255.255/, http://172.15.255.255/",
        "http://172.32.0.0/, http://172.32.0.0/",
        "http://192.167.255.255/, http://192.167.255.255/",
        "http://192.169.0.0/, http://192.169.0.0/",
        "http://169.253.255.255/, http://169.253.255.255/",
        "http://169.255.0.0/, http://169.255.0.0/",
        "http://100.63.255.255/, http://100.63.255.255/",
        "http://100.128.0.0/, http://100.128.0.0/",
        "http://1.0.0.0/, http://1.0.0.0/",
        "http://1.0x/, http://1.0.0.0/",
        "http://198.17.255.255/, http://198.17.255.255/",
        "http://198.20.0.0/, http://198.20.0.0/",
        "http://223.255.255.255/, http://223.255.255.255/",
        "http://255.255.255.254/, http://255.255.255.254/",
        // alternate encodings of a public address are stored as dotted decimal
        "http://134744072/, http://8.8.8.8/",
        "http://0x08080808/, http://8.8.8.8/",
        "http://010.010.010.010/, http://8.8.8.8/",
        "http://8.526344/, http://8.8.8.8/",
        // public and boundary IPv6
        "http://[2001:db8::1]/, http://[2001:db8::1]/",
        "http://[FBFF::1]:8080/, http://[fbff::1]:8080/",
        "http://[fe00::1]/, http://[fe00::1]/",
        "http://[::ffff:8.8.8.8]/, http://[::ffff:8.8.8.8]/",
        "http://[64:ff9b::8.8.8.8]/, http://[64:ff9b::8.8.8.8]/",
        "http://[2002:808:808::1]/, http://[2002:808:808::1]/",
        "http://[fe7f::1]/, http://[fe7f::1]/",
        // names that only look like localhost
        "http://localhost.example.com/, http://localhost.example.com/",
        "http://mylocalhost/, http://mylocalhost/",
        // host is lowercased; port, path, query and fragment are kept
        "https://Example.COM:8080/Path?Q=1#F, https://example.com:8080/Path?Q=1#F",
        "https://example.com/%0d%0aStays-Encoded, https://example.com/%0d%0aStays-Encoded"
    })
    @DisplayName("AC13, S-02: public hosts are accepted, with literal IPs normalized to their canonical form")
    void acceptsPublicHosts(String target, String normalized) {
        assertThat(policy.evaluate(target)).isEqualTo(new Accepted(normalized));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "https://user@example.com/",
                "https://user:pass@example.com/",
                "https://bank.com@evil.com/",
                "https://@example.com/",
                "https://user:@example.com/",
                "HTTPS://User%40corp:p%3Ass@example.com/",
                "https://user@127.0.0.1/"
            })
    @DisplayName("AC14, S-03: a target with userinfo is rejected with USERINFO_NOT_ALLOWED")
    void rejectsUserinfo(String target) {
        assertThat(policy.evaluate(target)).isEqualTo(new Rejected(UrlRejectionReason.USERINFO_NOT_ALLOWED));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "https://sho.rt/abc1234",
                "http://sho.rt/",
                "https://SHO.RT/x",
                "https://sho.rt./x",
                "https://sho.rt:8443/x",
                "https://ｓｈｏ.ｒｔ/x" // fullwidth letters fold to sho.rt
            })
    @DisplayName("AC15, S-04: a target on the service's own public host is rejected with SELF_REFERENCE")
    void rejectsSelfReference(String target) {
        assertThat(policy.evaluate(target)).isEqualTo(new Rejected(UrlRejectionReason.SELF_REFERENCE));
    }

    @Test
    @DisplayName("AC15, S-04: the public host is matched whatever the case or port in PUBLIC_BASE_URL")
    void selfReferenceUsesNormalizedPublicHost() {
        StandardUrlPolicy policy = new StandardUrlPolicy("HTTPS://Sho.RT:8443/");

        assertThat(policy.evaluate("https://sho.rt/x")).isEqualTo(new Rejected(UrlRejectionReason.SELF_REFERENCE));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                // whitespace and control characters inside the URL
                "https://exa mple.com/",
                "https://example.com/a b",
                "https://example.com/\r\nSet-Cookie: x=y",
                "https://example.com/\nx",
                "https://example.com/\tx",
                "https://example.com/\u0000",
                "https://example.com/\u007f",
                "https://example.com/\u0085",
                "https://example.com/\u00a0",
                "https://example.com/\u2028",
                "https://example.com/\u202eevil",
                // does not parse, or has no host
                "https://",
                "https:///path",
                "https:example.com",
                "https://exa<mple.com/",
                "https://example.com\\@evil.com/",
                "https://[::1/",
                "https://[1:2:3]/",
                "https://[::1]x/",
                "https://a..b/",
                // bad port
                "https://example.com:99999/",
                "https://example.com:12ab/",
                // percent-encoded host, which browsers would decode
                "http://%31%32%37.0.0.1/",
                "http://exa%6dple.com/",
                // ends in a number but is not a valid IPv4 address (browsers refuse these)
                "http://1.2.3.256/",
                "http://1.2.3.4.5/",
                "http://4294967296/",
                "http://0x100000000/",
                "http://1.2.3.09/",
                "http://example.123/"
            })
    @DisplayName(
            "AC16, S-05: a target that does not parse or has control characters or whitespace is rejected with MALFORMED")
    void rejectsMalformed(String target) {
        assertThat(policy.evaluate(target)).isEqualTo(new Rejected(UrlRejectionReason.MALFORMED));
    }

    @ParameterizedTest(name = "[{index}] length {1}")
    @MethodSource("tooLongTargets")
    @DisplayName("AC16, S-05: a target over 2,048 characters, before or after normalization, is rejected with TOO_LONG")
    void rejectsTooLong(String target, int length) {
        assertThat(policy.evaluate(target)).isEqualTo(new Rejected(UrlRejectionReason.TOO_LONG));
    }

    static Stream<Arguments> tooLongTargets() {
        String overByOne = padTo("https://example.com/", MAX_LENGTH + 1);
        String hugely = padTo("https://example.com/", 100_000);
        // 2,048 characters as typed; the punycode host makes the stored form longer
        String growsOnNormalization = padTo("https://bücher.example/", MAX_LENGTH);
        return Stream.of(overByOne, hugely, growsOnNormalization).map(target -> Arguments.of(target, target.length()));
    }

    @Test
    @DisplayName("AC16, S-05: a target of exactly 2,048 characters is accepted")
    void acceptsMaximumLength() {
        String target = padTo("https://example.com/", MAX_LENGTH);

        assertThat(policy.evaluate(target)).isEqualTo(new Accepted(target));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "https://bücher.example/a, https://xn--bcher-kva.example/a",
        "https://BÜCHER.example/, https://xn--bcher-kva.example/",
        "https://münchen.de:8080/x?q=1#f, https://xn--mnchen-3ya.de:8080/x?q=1#f",
        "https://例え.テスト/, https://xn--r8jz45g.xn--zckzah/",
        "https://xn--bcher-kva.example/, https://xn--bcher-kva.example/"
    })
    @DisplayName("AC17, S-05: an internationalized host is stored and returned as punycode")
    void convertsInternationalHostToPunycode(String target, String normalized) {
        assertThat(policy.evaluate(target)).isEqualTo(new Accepted(normalized));
    }

    private static String padTo(String prefix, int length) {
        return prefix + "a".repeat(length - prefix.length());
    }
}
