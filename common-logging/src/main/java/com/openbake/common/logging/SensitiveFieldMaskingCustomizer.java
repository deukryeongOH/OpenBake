package com.openbake.common.logging;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * 구조화 로그의 JSON 값에서 민감 정보를 가린다.
 *
 * <p>구조화 로깅을 켜면 로그가 중앙 저장소(Loki)로 흘러가 <b>오래 보관되고 검색
 * 가능해진다.</b> 지금까지는 Pod가 죽으면 로그도 사라져 노출 창이 짧았지만, 앞으로는
 * 한 번 새어 나간 값이 계속 남는다. 그래서 로그를 모으기 전에 마스킹을 먼저 넣는다.
 *
 * <p>두 가지를 가린다.
 *
 * <ol>
 *   <li><b>키 이름 기준</b> — MDC나 구조화 필드에 {@code password}, {@code token},
 *       {@code cardNumber} 같은 이름이 있으면 값을 통째로 가린다.
 *   <li><b>본문 패턴 기준</b> — 로그 메시지 문자열 안에 섞여 들어온 카드번호·주민번호
 *       형태를 정규식으로 가린다. 개발자가 {@code log.info("카드 {}", cardNo)}처럼
 *       쓰는 경우를 잡는다.
 * </ol>
 *
 * <p><b>이것이 완전한 방어가 아니라는 점을 분명히 한다.</b> 정규식은 알려진 형태만
 * 잡는다. 근본 대책은 애초에 민감 값을 로그에 넣지 않는 것이고, 이 클래스는 마지막
 * 그물이다. 09번 문서가 경고한 bind parameter 로그는 이미 prod에서
 * {@code org.hibernate.orm.jdbc.bind: WARN}으로 꺼져 있다.
 *
 * <p>구조화 로깅은 prod 프로필에서만 켜므로 이 커스터마이저도 그때만 동작한다.
 * 로컬에서는 사람이 읽는 형식을 그대로 쓴다.
 */
public class SensitiveFieldMaskingCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    private static final String MASK = "***";

    /**
     * 값을 가릴 키 조각. 소문자로 비교하며 <b>부분 일치</b>다.
     * {@code accessToken}, {@code refresh_token}, {@code X-Auth-Token}이 모두 걸린다.
     */
    private static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "password", "passwd", "secret", "token", "authorization",
            "credential", "apikey", "api_key",
            "cardnumber", "card_no", "cardno", "cvc", "cvv",
            "ssn", "residentregistration", "account_no", "accountno");

    /**
     * 로그 <b>메시지 안</b>에 섞여 들어온 값을 잡는 패턴.
     *
     * <p>구분자를 허용하는 이유: 카드번호는 {@code 1234-5678-9012-3456}로도
     * {@code 1234 5678 9012 3456}로도 찍힌다.
     */
    private static final Pattern CARD_NUMBER =
            Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b");

    /** 주민등록번호. 뒷자리 첫 숫자까지만 남기지 않고 전부 가린다. */
    private static final Pattern RRN =
            Pattern.compile("\\b\\d{6}[- ]?[1-4]\\d{6}\\b");

    /**
     * {@code key=value} 또는 {@code key: value} 형태로 메시지에 박힌 민감 값.
     *
     * <p>{@code log.info("token={}", t)} 처럼 쓰면 최종 문자열이
     * {@code token=eyJhbGci...}가 되어 키 기준 마스킹으로는 못 잡는다.
     */
    private static final Pattern INLINE_SENSITIVE = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|authorization|credential|api[_-]?key)"
                    + "\\s*[=:]\\s*\\S+");

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(this::mask);
    }

    private Object mask(JsonWriter.MemberPath path, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(path)) {
            return MASK;
        }
        if (value instanceof CharSequence text) {
            return maskInText(text.toString());
        }
        return value;
    }

    private static boolean isSensitiveKey(JsonWriter.MemberPath path) {
        String name = path == null ? null : path.toString();
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String part : SENSITIVE_KEY_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static String maskInText(String text) {
        // 스택 트레이스나 긴 본문에서 정규식 세 번은 비용이 있다. 후보 문자가 아예
        // 없으면 건너뛴다 — 대부분의 로그가 여기서 빠져나간다.
        if (!hasMaskCandidate(text)) {
            return text;
        }
        String masked = CARD_NUMBER.matcher(text).replaceAll(MASK);
        masked = RRN.matcher(masked).replaceAll(MASK);
        masked = INLINE_SENSITIVE.matcher(masked).replaceAll(matcher ->
                matcher.group(1) + "=" + MASK);
        return masked;
    }

    /** 숫자가 6자리 이상 이어지거나 민감 키워드가 보일 때만 정규식을 돌린다. */
    private static boolean hasMaskCandidate(String text) {
        int digits = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                if (++digits >= 6) {
                    return true;
                }
            } else if (c != '-' && c != ' ') {
                digits = 0;
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("token") || lower.contains("password")
                || lower.contains("secret") || lower.contains("credential")
                || lower.contains("authorization") || lower.contains("api");
    }
}
