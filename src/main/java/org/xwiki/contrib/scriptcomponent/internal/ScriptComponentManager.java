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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.ComponentAnnotationLoader;
import org.xwiki.component.descriptor.ComponentDescriptor;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.wiki.WikiComponentException;
import org.xwiki.component.wiki.WikiComponentScope;
import org.xwiki.model.ModelContext;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Register/unregister script based components.
 * 
 * @version $Id$
 */
@Component(roles = ScriptComponentManager.class)
@Singleton
public class ScriptComponentManager
{
    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private DocumentAccessBridge documentAccessBridge;

    @Inject
    private ModelContext modelContext;

    @Inject
    private AuthorizationManager authorization;

    @Inject
    @Named("root")
    private ComponentManager rootComponentManager;

    @Inject
    private ScriptComponentExecutor scriptComponentExecutor;

    @Inject
    private Logger logger;

    private ComponentAnnotationLoader componentAnnotationLoader = new ComponentAnnotationLoader();

    private final ConcurrentMap<DocumentReference, List<ComponentDescriptor<?>>> componentIndex =
        new ConcurrentHashMap<>();

    private WikiComponentScope getScope(BaseObject xobject)
    {
        String scopeString = xobject.getStringValue(ScriptComponentClassInitializer.CP_SCOPE);

        if (StringUtils.isEmpty(scopeString) || scopeString.equals("NONE")) {
            return null;
        }

        return WikiComponentScope.fromString(scopeString);
    }

    /**
     * @param document the document containing the components to register
     * @throws WikiComponentException when failing to register document associated with the passed document
     */
    public void registerComponents(XWikiDocument document) throws WikiComponentException
    {
        BaseObject xobject = document.getXObject(ScriptComponentClassInitializer.CLASS_REFERENCE);

        if (xobject == null) {
            return;
        }

        WikiComponentScope scope = getScope(xobject);

        if (scope == null) {
            return;
        }

        String language = xobject.getStringValue(ScriptComponentClassInitializer.CP_LANGUAGE);

        if (StringUtils.isEmpty(language)) {
            return;
        }

        String script = xobject.getStringValue(ScriptComponentClassInitializer.CP_SCRIPT);

        if (StringUtils.isEmpty(script)) {
            return;
        }

        XWikiDocument ownerDocument = xobject.getOwnerDocument();

        DocumentReference componentAuthor = ownerDocument.getAuthorReference();

        // Before going further we need to check the document author is authorized to register the component
        checkRights(componentAuthor);

        // Get the component manager
        ComponentManager componentManager = getComponentManager(scope);

        // Execute the script
        Class<?> componentClass = this.scriptComponentExecutor.getClass(language, script, document);

        // Extract the components
        List<ComponentDescriptor<?>> components =
            (List) this.componentAnnotationLoader.getComponentsDescriptors(componentClass);

        // Register the components

        try {
            registerComponents(components, componentAuthor, ownerDocument.getDocumentReference(), componentManager);
        } finally {
            this.componentIndex.put(document.getDocumentReference(), components);
        }
    }

    private ComponentManager getComponentManager(WikiComponentScope scope) throws WikiComponentException
    {
        ComponentManager cm;

        try {
            switch (scope) {
                case USER:
                    cm = this.rootComponentManager.getInstance(ComponentManager.class, "user");
                    break;
                case WIKI:
                    cm = this.rootComponentManager.getInstance(ComponentManager.class, "wiki");
                    break;
                default:
                    cm = this.rootComponentManager;
            }
        } catch (ComponentLookupException e) {
            throw new WikiComponentException("Failed to get component manager for scope [" + scope + "]", e);
        }

        return cm;
    }

    private void checkRights(DocumentReference componentAuthor) throws WikiComponentException
    {
        if (!this.authorization.hasAccess(Right.PROGRAM, componentAuthor, null)) {
            throw new WikiComponentException("Registering script component requires programming rights");
        }
    }

    private void registerComponents(List<ComponentDescriptor<?>> components, DocumentReference componentAuthor,
        DocumentReference componentDocument, ComponentManager componentManager)
    {
        // Save current context information
        DocumentReference currentUserReference = getCurrentUserReference();
        EntityReference currentEntityReference = getCurrentEntityReference();

        try {
            // Set the proper information so the component manager use the proper keys to find components to register
            setCurrentUserReference(componentAuthor);
            setCurrentEntityReference(componentDocument);

            // Register the components against the Component Manager
            for (ComponentDescriptor<?> component : components) {
                try {
                    componentManager.registerComponent(component);
                } catch (Exception e) {
                    this.logger.error("Failed to register component [{}]", component);
                }
            }
        } finally {
            setCurrentUserReference(currentUserReference);
            setCurrentEntityReference(currentEntityReference);
        }
    }

    /**
     * @param document the document containing the components to unregister
     * @throws WikiComponentException when failing to unregister components associated with the passed document
     */
    public void unregisterComponents(XWikiDocument document) throws WikiComponentException
    {
        BaseObject component =
            document.getOriginalDocument().getXObject(ScriptComponentClassInitializer.CLASS_REFERENCE);

        if (component != null) {
            List<ComponentDescriptor<?>> components = this.componentIndex.remove(document.getDocumentReference());

            WikiComponentScope scope = getScope(component);

            if (scope != null) {
                unregisterComponents(components, scope);
            }
        }
    }

    private void unregisterComponents(List<ComponentDescriptor<?>> components, WikiComponentScope scope)
        throws WikiComponentException
    {
        ComponentManager componentManager = getComponentManager(scope);

        for (ComponentDescriptor<?> component : components) {
            componentManager.unregisterComponent(component);
        }
    }

    private DocumentReference getCurrentUserReference()
    {
        return this.documentAccessBridge.getCurrentUserReference();
    }

    private void setCurrentUserReference(DocumentReference reference)
    {
        this.documentAccessBridge.setCurrentUser(this.serializer.serialize(reference));
    }

    private EntityReference getCurrentEntityReference()
    {
        return this.modelContext.getCurrentEntityReference();
    }

    private void setCurrentEntityReference(EntityReference reference)
    {
        this.modelContext.setCurrentEntityReference(reference);
    }
}
