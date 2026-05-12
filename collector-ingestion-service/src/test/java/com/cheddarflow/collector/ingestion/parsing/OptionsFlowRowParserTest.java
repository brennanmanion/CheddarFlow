package com.cheddarflow.collector.ingestion.parsing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionsFlowRowParserTest {
    private final OptionsFlowRowParser parser = new OptionsFlowRowParser();

    @Test
    void parsesLiveStyleRowHtml() {
        String html = """
                <div role="gridcell" col-id="time" tabindex="-1">10:55:19 AM</div>
                <div role="gridcell" col-id="expiry" tabindex="-1"><button type="button">10/16/2026</button></div>
                <div role="gridcell" col-id="symbol" tabindex="-1"><button type="button">NXPI</button></div>
                <div role="gridcell" col-id="strike" tabindex="-1">310</div>
                <div role="gridcell" col-id="spot" tabindex="-1">295.18</div>
                <div role="gridcell" col-id="putCall" tabindex="-1">Call</div>
                <div role="gridcell" col-id="side" tabindex="-1">Mid</div>
                <div role="gridcell" col-id="buySell" tabindex="-1"></div>
                <div role="gridcell" col-id="size" tabindex="-1">600</div>
                <div role="gridcell" col-id="price" tabindex="-1">$28.5</div>
                <div role="gridcell" col-id="premium" tabindex="-1">$1.7M</div>
                <div role="gridcell" col-id="sweepBlockSplit" tabindex="-1">Sweep</div>
                <div role="gridcell" col-id="volume" tabindex="-1">600</div>
                <div role="gridcell" col-id="openInt" tabindex="-1">82</div>
                <div role="gridcell" col-id="conds" tabindex="-1">
                  <div class="chakra-wrap">
                    <ul class="chakra-wrap__list">
                      <li><span>AUTO</span></li>
                      <li><span>opening</span></li>
                      <li><span>unusual</span></li>
                    </ul>
                  </div>
                </div>
                """;

        ParsedOptionsFlowRow parsed = parser.parse(html);

        assertEquals("10:55:19 AM", parsed.eventTimeText());
        assertEquals("NXPI", parsed.symbol());
        assertEquals("10/16/2026", parsed.expiry());
        assertEquals("310", parsed.strike());
        assertEquals("Call", parsed.putCall());
        assertEquals("Mid", parsed.side());
        assertEquals("", parsed.buySell());
        assertEquals("$1.7M", parsed.premiumText());
        assertEquals(new BigDecimal("1700000.0"), parsed.premiumNumeric());
        assertEquals("AUTO opening unusual", parsed.conditions());
    }

    @Test
    void scalesDecimalPremiumSuffixesCorrectly() {
        String html = """
                <div role="gridcell" col-id="time" tabindex="-1">09:30:00 AM</div>
                <div role="gridcell" col-id="tick" tabindex="-1">SPY</div>
                <div role="gridcell" col-id="premium" tabindex="-1">$1.5M</div>
                <div role="gridcell" col-id="conds" tabindex="-1"><div><ul><li><span>AUTO</span></li></ul></div></div>
                """;

        ParsedOptionsFlowRow parsed = parser.parse(html);

        assertEquals("SPY", parsed.symbol());
        assertEquals("", parsed.expiry());
        assertEquals("$1.5M", parsed.premiumText());
        assertEquals(new BigDecimal("1500000.0"), parsed.premiumNumeric());
        assertEquals("AUTO", parsed.conditions());
    }
}
