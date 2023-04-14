package de.cxp.ocs.elasticsearch.query;

import de.cxp.ocs.spi.search.UserQueryPreprocessor;

import java.util.Map;
import java.util.Optional;

/**
 * Filters everything that is not in the configured character point range.
 * Characters out of range will be replaced by a single whitespace.
 *
 * default settings:
 *  code_point_lower_bound: 0
 *  code_point_upper_bound: Integer.MAX_VALUE
 *
 * @author Gabriel Bauer
 */
public class CodePointFilterUserQueryPreprocessor implements UserQueryPreprocessor {

    private static final String CODE_POINT_LOWER_BOUND_PROPERTY_NAME = "code_point_lower_bound";
    private static final String CODE_POINT_UPPER_BOUND_PROPERTY_NAME = "code_point_upper_bound";
    private int codePointLowerBound;
    private int codePointUpperBound;

    @Override
    public void initialize(Map<String, String> settings) {
        UserQueryPreprocessor.super.initialize(settings);
        codePointLowerBound = Optional.ofNullable(settings.get(CODE_POINT_LOWER_BOUND_PROPERTY_NAME)).map(Integer::parseInt).orElse(0);
        codePointUpperBound = Optional.ofNullable(settings.get(CODE_POINT_UPPER_BOUND_PROPERTY_NAME)).map(Integer::parseInt).orElse(Integer.MAX_VALUE);
        if (codePointLowerBound < 0) {
            throw new IllegalArgumentException(CODE_POINT_LOWER_BOUND_PROPERTY_NAME + " must be greater than 0.");
        }
        if (codePointUpperBound < 0) {
            throw new IllegalArgumentException(CODE_POINT_UPPER_BOUND_PROPERTY_NAME + " must be greater than 0.");
        }
        if (codePointLowerBound >= codePointUpperBound) {
            throw new IllegalArgumentException(CODE_POINT_UPPER_BOUND_PROPERTY_NAME + " must be greater than " + CODE_POINT_LOWER_BOUND_PROPERTY_NAME + ".");
        }
    }

    @Override
    public String preProcess(String userQuery) {
        StringBuilder sb = new StringBuilder(userQuery.length());
        for (int i = 0; i < userQuery.length(); i++) {
            int codePoint = userQuery.codePointAt(i);
            if (codePoint < codePointLowerBound || codePoint > codePointUpperBound) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                } else {
                    continue;
                }
            }
            sb.append(userQuery.charAt(i));
        }
        return sb.toString();
    }
}
