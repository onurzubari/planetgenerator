package com.onur.planetgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MappingTest {
    @Test
    void twoToOneAspect() {
        int W = 4096, H = 2048;
        assertEquals(W, 2 * H);
    }
}
