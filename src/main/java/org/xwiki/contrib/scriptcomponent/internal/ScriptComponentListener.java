/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.scriptcomponent.internal;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.ApplicationReadyEvent;
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.bridge.event.WikiReadyEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Automatically register/unregister script based components.
 * 
 * @version $Id: 28c0dbaa4803fa1896b5a961e9f059a5ad1d4286 $
 */
@Component
@Singleton
@Named(ScriptComponentClassInitializer.CLASS_REFERENCE_STRING)
public class ScriptComponentListener extends AbstractEventListener
{
    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentDocumentReferenceResolver;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ScriptComponentManager scriptComponentManager;

    @Inject
    private Logger logger;

    /**
     * The default constructor.
     */
    public ScriptComponentListener()
    {
        super(ScriptComponentClassInitializer.CLASS_REFERENCE_STRING, new DocumentCreatedEvent(),
            new DocumentDeletedEvent(), new DocumentUpdatedEvent(), new WikiReadyEvent(), new ApplicationReadyEvent());
    }

    private void register(XWikiDocument document)
    {
        try {
            this.scriptComponentManager.registerComponents(document);
        } catch (Exception e) {
            this.logger.error("Failed to register components", e);
        }
    }

    private void unregister(XWikiDocument document)
    {
        try {
            this.scriptComponentManager.unregisterComponents(document);
        } catch (Exception e) {
            this.logger.error("Failed to unregister components", e);
        }
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        if (event instanceof DocumentDeletedEvent) {
            XWikiDocument document = (XWikiDocument) source;
            unregister(document.getOriginalDocument());
        } else if (event instanceof DocumentUpdatedEvent) {
            XWikiDocument document = (XWikiDocument) source;
            unregister(document.getOriginalDocument());
            register(document);
        } else if (event instanceof DocumentCreatedEvent) {
            XWikiDocument document = (XWikiDocument) source;
            register(document);
        } else {
            registerAll();
        }
    }

    private void registerAll()
    {
        List<String> results;
        try {
            Query query = this.queryManager.createQuery("select distinct doc.fullName from Document as doc, doc.object("
                + ScriptComponentClassInitializer.CLASS_REFERENCE_STRING + ") as obj", Query.XWQL);
            results = query.execute();
        } catch (QueryException e) {
            this.logger.error("Failed to search for script components", e);

            return;
        }

        XWikiContext xcontext = this.xcontextProvider.get();

        for (String result : results) {
            DocumentReference sourceDocumentReference = this.currentDocumentReferenceResolver.resolve(result);

            try {
                XWikiDocument document = xcontext.getWiki().getDocument(sourceDocumentReference, xcontext);

                register(document);
            } catch (Exception e) {
                this.logger.error("Failed to register script components located in document [{}]",
                    sourceDocumentReference, e);
            }
        }

    }

}
