package uz.kapitalbank.umida;

import io.jmix.core.CoreProperties;
import io.jmix.core.metamodel.datatype.DatatypeRegistry;
import io.jmix.core.metamodel.datatype.FormatStrings;
import io.jmix.core.metamodel.datatype.FormatStringsRegistry;
import io.jmix.core.metamodel.datatype.TimeZoneAwareDatatype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every locale shows a LocalDate as {@code dd.MM.yyyy}, a LocalDateTime and an OffsetDateTime as
 * {@code dd.MM.yyyy HH:mm:ss}; an OffsetDateTime is shifted to the user's time zone and its offset
 * is not shown.
 */
@SpringBootTest
public class DateTimeFormatTests {

    private static final String DATE_FORMAT = "dd.MM.yyyy";
    private static final String DATE_TIME_FORMAT = "dd.MM.yyyy HH:mm:ss";

    @Autowired
    CoreProperties coreProperties;

    @Autowired
    FormatStringsRegistry formatStringsRegistry;

    @Autowired
    DatatypeRegistry datatypeRegistry;

    @Test
    void everyLocaleShowsDateTimesInTheSameFormat() {
        for (Locale locale : coreProperties.getAvailableLocales()) {
            FormatStrings formatStrings = formatStringsRegistry.getFormatStrings(locale);
            assertNotNull(formatStrings, locale.toString());
            assertEquals(DATE_FORMAT, formatStrings.getDateFormat(), locale.toString());
            assertEquals(DATE_TIME_FORMAT, formatStrings.getDateTimeFormat(), locale.toString());
            assertEquals(DATE_TIME_FORMAT, formatStrings.getOffsetDateTimeFormat(), locale.toString());

            assertEquals("05.03.2026", datatypeRegistry.get(LocalDate.class)
                    .format(LocalDate.of(2026, 3, 5), locale), locale.toString());
            assertEquals("05.03.2026 14:07:09", datatypeRegistry.get(LocalDateTime.class)
                    .format(LocalDateTime.of(2026, 3, 5, 14, 7, 9), locale), locale.toString());
        }
    }

    @Test
    void anOffsetDateTimeIsShownInTheUserTimeZoneWithoutTheOffset() {
        TimeZoneAwareDatatype datatype = (TimeZoneAwareDatatype) datatypeRegistry.get(OffsetDateTime.class);
        // 23:30 UTC is already the next day in Tashkent (UTC+5)
        OffsetDateTime value = OffsetDateTime.of(2026, 3, 5, 23, 30, 15, 0, ZoneOffset.UTC);

        for (Locale locale : coreProperties.getAvailableLocales()) {
            assertEquals("06.03.2026 04:30:15",
                    datatype.format(value, locale, TimeZone.getTimeZone("Asia/Tashkent")), locale.toString());
        }
    }
}
