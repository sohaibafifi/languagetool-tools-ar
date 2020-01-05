/* Dev Tools for Arabic and LanguageTool
 * Copyright (C) 2020 Sohaib Afifi
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package dev.bigdata;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.languagetool.dev.bigdata.TextToNgram;

import java.io.File;
import java.io.IOException;

public class TextToNgramTest {

  @Test
  @Ignore("Interactive use only, has not assertions")
  public void testIndexing() throws IOException {
    File tempDir = new File(FileUtils.getTempDirectory(), "text-to-ngram-test");
    try {
      tempDir.mkdir();
      String filename = TextToNgramTest.class.getResource("/org/languagetool/dev/bigdata/sentences_ar.txt").getFile();
      try (TextToNgram prg = new TextToNgram(new File(filename), tempDir)) {
        prg.setCacheLimit(1);
        prg.indexInputFile();
      }
    } finally {
      FileUtils.deleteDirectory(tempDir);
    }
  }
}
