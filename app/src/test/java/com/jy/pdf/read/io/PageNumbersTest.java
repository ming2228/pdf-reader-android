package com.jy.pdf.read.io;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PageNumbersTest {
    @Test public void keepsValidPage(){assertEquals(23,PageNumbers.normalize("23",163));}
    @Test public void capsAtLastPage(){assertEquals(163,PageNumbers.normalize("999",163));}
    @Test public void capsArbitrarilyLongNumericInput(){assertEquals(163,PageNumbers.normalize("999999999999999999999999999999",163));}
    @Test public void invalidEmptyAndNegativeBecomeFirstPage(){for(String s:new String[]{""," ","-5","abc","1.5","3x","0",null})assertEquals(1,PageNumbers.normalize(s,163));}
    @Test public void handlesWhitespaceAndLeadingZeros(){assertEquals(25,PageNumbers.normalize(" 00025 ",163));assertEquals(163,PageNumbers.normalize("000999",163));}
    @Test public void aSinglePageAlwaysClampsToOne(){assertEquals(1,PageNumbers.normalize("999",1));}
}
