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
import org.jetbrains.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Test;
import org.languagetool.Language;
import org.languagetool.UserConfig;
import org.languagetool.chunking.Chunker;
import org.languagetool.dev.bigdata.TextToNgram;
import org.languagetool.language.Contributor;
import org.languagetool.languagemodel.LanguageModel;
import org.languagetool.rules.Rule;
import org.languagetool.tagging.Tagger;
import org.languagetool.tagging.xx.DemoTagger;
import org.languagetool.tokenizers.WordTokenizer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class TextToNgramTest {

  @Test
  @Ignore("Interactive use only, has not assertions")
  public void testIndexing() throws IOException {
    File tempDir = new File(FileUtils.getTempDirectory(), "text-to-ngram-test");
    try {
      tempDir.mkdir();
      String filename = TextToNgramTest.class.getResource("/org/languagetool/dev/bigdata/sentences_ar.txt").getFile();
      try (TextToNgram prg = new TextToNgram(new Arabic(), new File(filename), tempDir)) {
        prg.setCacheLimit(1);
        prg.indexInputFile();
      }
    } finally {
      FileUtils.deleteDirectory(tempDir);
    }
  }

  public class Arabic extends Language implements AutoCloseable {

    private WordTokenizer wordTokenizer;
    private DemoTagger tagger;
    private LanguageModel languageModel;

    @Override
    public String getName() {
      return "Arabic";
    }

    @Override
    public String getShortCode() {
      return "ar";
    }

    @Override
    public String[] getCountries() {
      return new String[]{"", "SA", "DZ", "BH", "EG", "IQ", "JO", "KW", "LB", "LY", "MA", "OM", "QA", "SD", "SY", "TN", "AE", "YE"};
    }

    @Nullable
    @Override
    public Contributor[] getMaintainers() {
      return new Contributor[0];
    }


    @Override
    public WordTokenizer getWordTokenizer() {
      if (wordTokenizer == null) {
        wordTokenizer = new TextToNgram.ArabicWordTokenizer();
      }
      return wordTokenizer;
    }


    @Override
    public List<Rule> getRelevantRules(ResourceBundle messages, UserConfig userConfig, Language motherTongue, List<Language> altLanguages) throws IOException {
      return Arrays.asList(

      );
    }

    @Override
    public Tagger getTagger() {
      if (tagger == null) {
        tagger = new DemoTagger();
      }
      return tagger;
    }

    @Override
    public Chunker getChunker() {
      return null;
    }

    @Override
    public synchronized LanguageModel getLanguageModel(File indexDir) throws IOException {
      languageModel = initLanguageModel(indexDir, languageModel);
      return languageModel;
    }

    @Override
    public void close() throws Exception {
      if (languageModel != null) {
        languageModel.close();
      }
    }
  }

}
