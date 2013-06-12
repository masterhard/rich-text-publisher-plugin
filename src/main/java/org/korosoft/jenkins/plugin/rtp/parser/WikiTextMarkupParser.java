package org.korosoft.jenkins.plugin.rtp.parser;

import org.korosoft.jenkins.plugin.rtp.MarkupParser;
import org.sweble.wikitext.engine.CompiledPage;
import org.sweble.wikitext.engine.Compiler;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.utils.HtmlPrinter;
import org.sweble.wikitext.engine.utils.SimpleWikiConfiguration;

import javax.xml.bind.JAXBException;
import java.io.FileNotFoundException;
import java.io.StringWriter;

/*

The New BSD License

Copyright (c) 2011-2013, Dmitry Korotkov
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

- Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright notice, this
  list of conditions and the following disclaimer in the documentation and/or
  other materials provided with the distribution.

- Neither the name of the Jenkins RuSalad Plugin nor the names of its
  contributors may be used to endorse or promote products derived from this
  software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

/**
 * WikiText markup parser
 *
 * @author Dmitry Korotkov
 * @since 1.0
 */
public class WikiTextMarkupParser implements MarkupParser {

    private SimpleWikiConfiguration config;
    private org.sweble.wikitext.engine.Compiler compiler;

    private void init() {
        try {
            config = new SimpleWikiConfiguration("classpath:/org/sweble/wikitext/engine/SimpleWikiConfiguration.xml");
            compiler = new Compiler(config);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public String parse(String markupText) {
        if (config == null || compiler == null) {
            init();
        }

        try {
            PageTitle pageTitle = PageTitle.make(config, "PageTitle");
            PageId pageId = new PageId(pageTitle, -1);
            CompiledPage cp = compiler.postprocess(pageId, markupText, null);
            StringWriter w = new StringWriter();
            HtmlPrinter p = new HtmlPrinter(w, pageTitle.getFullTitle());
            p.setStandaloneHtml(false, "");
            p.go(cp.getPage());
            return w.toString();
        } catch (Exception e) {
            return "<b>Failed to compile message:</b><br/>" + e.toString();
        }
    }

    public String getName() {
        return "Wiki";
    }
}
