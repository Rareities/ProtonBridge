package io.github.rareities.protonbridge.util;

import io.github.rareities.protonbridge.Items.FilterEntry;
import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Exercises the shared filter builder against a real engine using disposable local files. */
public class NativeFilterTest {
    private String run(File root, String... args) throws Exception {
        String executable = System.getenv("PROTONBRIDGE_TEST_RCLONE");
        Assume.assumeTrue(executable != null && new File(executable).isFile());
        List<String> command = new ArrayList<>(); command.add(executable);
        command.addAll(Arrays.asList(args)); command.add("--config=" + new File(root,"absent.conf"));
        File log = new File(root,"output.log");
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start();
        if (!child.waitFor(30, TimeUnit.SECONDS)) { child.destroyForcibly(); fail("Native filter fixture timed out"); }
        String output = new String(Files.readAllBytes(log.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(output, 0, child.exitValue()); return output;
    }
    private void remove(File file) { File[] children=file.listFiles(); if(children!=null) for(File child:children) remove(child); file.delete(); }

    @Test(timeout=120000) public void rootAndRecursiveMarkerFiltersMatchWithoutHidingDecks() throws Exception {
        File root=Files.createTempDirectory("protonbridge-filter-").toFile();
        try {
            File right=new File(root,"right"); new File(right,"nested").mkdirs();
            for(String name:Arrays.asList(".nomedia","deck.anki2","nested/.nomedia","nested/deck.anki2","extra.txt"))
                Files.write(new File(right,name).toPath(),name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for(String marker:Arrays.asList("/.nomedia","**/.nomedia",".nomedia")) {
                List<String> args=new ArrayList<>(Arrays.asList("lsf",right.toString(),"--recursive","--files-only"));
                args.addAll(FilterRules.arguments(Collections.emptyList(),Arrays.asList("--include=**","--exclude=\""+marker+"\"")));
                List<String> listed=Arrays.asList(run(root,args.toArray(new String[0])).split("\\r?\\n"));
                assertEquals(marker.equals("**/.nomedia"),listed.contains(".nomedia"));
                assertEquals(marker.equals("/.nomedia"),listed.contains("nested/.nomedia"));
                assertTrue(listed.contains("nested/deck.anki2"));
            }
            File left=new File(root,"left"); left.mkdir();
            List<String> filter=FilterRules.arguments(Collections.singletonList(new FilterEntry(FilterEntry.FILTER_INCLUDE,"*.anki2")),Collections.singletonList("--exclude=.nomedia"));
            for(String operation:Arrays.asList("initialise","run")) {
                List<String> args=new ArrayList<>(Arrays.asList("bisync",left.toString(),right.toString(),"--workdir="+new File(root,"baseline")));
                args.addAll(filter);
                if(!operation.equals("run")) args.addAll(Arrays.asList("--resync","--resync-mode=path2"));
                run(root,args.toArray(new String[0]));
                assertTrue(new File(left,"nested/deck.anki2").exists());
                assertFalse(new File(left,".nomedia").exists()); assertFalse(new File(left,"nested/.nomedia").exists());
                assertFalse(new File(left,"extra.txt").exists());
                assertTrue(new File(right,".nomedia").exists()); // Exclusion never deletes the source marker.
            }
        } finally { remove(root); }
    }
}
