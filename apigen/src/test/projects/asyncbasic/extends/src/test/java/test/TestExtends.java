package test;

import org.junit.Test;
import static org.junit.Assert.*;


public class TestExtends {
    public void test() {
        Extends ext = new Extends.Builder()
            .withBase(new Base.Builder()
                      .withBase("string")
                      .build())
            .build();
        assertEquals("string", ext.getBase().getBase());

        // Not very exciting, but shows how to copy:
        Extends ext2 = new Extends.Builder(ext)
            .build();
        assertEquals("string", ext2.getBase().getBase());

        assertNotSame(ext, ext2);

    }
}
