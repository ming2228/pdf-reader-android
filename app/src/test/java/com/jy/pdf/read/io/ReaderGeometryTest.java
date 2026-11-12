package com.jy.pdf.read.io;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReaderGeometryTest {
    private ReaderGeometry geometry() {
        ReaderGeometry g=new ReaderGeometry(); g.pageW=600; g.pageH=1800; g.viewW=600; g.viewH=800; return g;
    }
    @Test public void wholePageCropIsValid() { assertTrue(ReaderGeometry.Crop.full().valid()); }
    @Test public void refusesNegativeEmptyAndNonfiniteCrop() {
        assertFalse(new ReaderGeometry.Crop(-.1,0,1,1).valid());
        assertFalse(new ReaderGeometry.Crop(.5,0,.5,1).valid());
        assertFalse(new ReaderGeometry.Crop(0,Double.NaN,1,1).valid());
    }
    @Test public void fitWidthPreservesTallPage() { ReaderGeometry g=geometry(); assertEquals(1000,g.maxY(),.001); assertEquals(0,g.maxX(),.001); }
    @Test public void cropRefitsRemainingWidthAndPreservesAspectRatio() {
        ReaderGeometry g=geometry();g.cropTo(new ReaderGeometry.Crop(.1,.1,.9,.9));
        assertEquals(1.25,g.scale(),.001);assertEquals(600,g.width(),.001);assertEquals(1800,g.height(),.001);
    }
    @Test public void zoomKeepsPdfPointUnderFingers() {
        ReaderGeometry g=geometry();g.y=300;g.zoomTo(2,300,400);
        assertEquals(300,g.x,.001);assertEquals(1000,g.y,.001);assertEquals(2,g.zoom,.001);
    }
    @Test public void zoomIsNeverSmallerThanFitWidth() { ReaderGeometry g=geometry();g.zoomTo(.1,0,0);assertEquals(1,g.zoom,.001);g.zoomTo(99,0,0);assertEquals(4,g.zoom,.001); }
    @Test public void scrollOffsetsClampToDocumentBounds() { ReaderGeometry g=geometry();g.zoom=2;g.x=99999;g.y=-5;g.clamp();assertEquals(600,g.x,.001);assertEquals(0,g.y,.001); }
    @Test public void nextKeyScrollsVerticallyAndLeavesHorizontalOffset() {
        ReaderGeometry g=geometry();g.zoom=2;g.x=210;assertEquals(0,g.step(1,false,0,5));assertEquals(640,g.y,.001);assertEquals(210,g.x,.001);
    }
    @Test public void reachingBottomDoesNotCrossPageInSamePress() {
        ReaderGeometry g=geometry();g.y=900;assertEquals(0,g.step(1,false,0,5));assertEquals(1000,g.y,.001);
        assertEquals(1,g.step(1,false,0,5));
    }
    @Test public void previousAtTopChangesPageOnlyIfPreviousExists() { ReaderGeometry g=geometry();assertEquals(-1,g.step(-1,false,2,5));assertEquals(0,g.step(-1,false,0,5)); }
    @Test public void heldKeyCannotSkipPagesAtBoundary() { ReaderGeometry g=geometry();g.y=1000;assertEquals(0,g.step(1,true,1,5));g.y=0;assertEquals(0,g.step(-1,true,1,5)); }
    @Test public void lastPageCannotAdvanceBeyondDocument() { ReaderGeometry g=geometry();g.y=1000;assertEquals(0,g.step(1,false,4,5)); }
    @Test public void shortPageKeysNavigateImmediately() { ReaderGeometry g=geometry();g.pageH=300;assertEquals(1,g.step(1,false,0,3));assertEquals(-1,g.step(-1,false,1,3)); }
    @Test public void scanCodeMappingTakesPrecedenceOverGenericKeycode() {
        assertEquals(-1,ReaderGeometry.keyDirection(115,25));assertEquals(1,ReaderGeometry.keyDirection(114,24));
    }
    @Test public void keyCodeFallbackWorksForVolumeAndPageKeys() {
        assertEquals(-1,ReaderGeometry.keyDirection(0,24));assertEquals(1,ReaderGeometry.keyDirection(0,25));
        assertEquals(-1,ReaderGeometry.keyDirection(0,92));assertEquals(1,ReaderGeometry.keyDirection(0,93));assertEquals(0,ReaderGeometry.keyDirection(116,26));
    }
    @Test public void positionRoundTripIsIndependentOfViewportSize() {
        ReaderGeometry g=geometry();g.zoom=2;g.x=200;g.y=900;double px=g.pageX(),py=g.pageY();
        g.viewW=800;g.viewH=1000;g.restore(px,py);
        assertEquals(px,g.pageX(),.001);assertEquals(py,g.pageY(),.001);
    }
}
