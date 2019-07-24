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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.doc.AbstractMandatoryClassInitializer;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

/**
 * Initialize the script component class.
 * 
 * @version $Id: 2c35c01b87fb476a060733aa5aa9f6d825c738be $
 */
@Component
@Named(ScriptComponentClassInitializer.CLASS_REFERENCE_STRING)
@Singleton
public class ScriptComponentClassInitializer extends AbstractMandatoryClassInitializer
{
    /**
     * The name of the class.
     */
    public static final String CLASS_DOCUMENT = "ScriptComponentClass";

    /**
     * The reference of the class as a String.
     */
    public static final String CLASS_REFERENCE_STRING = XWiki.SYSTEM_SPACE + '.' + CLASS_DOCUMENT;

    /**
     * The reference of the class.
     */
    public static final LocalDocumentReference CLASS_REFERENCE =
        new LocalDocumentReference(XWiki.SYSTEM_SPACE, CLASS_DOCUMENT);

    /**
     * The property containing the script content.
     */
    public static final String CP_SCRIPT = "script_content";

    /**
     * The property containing the script language.
     */
    public static final String CP_LANGUAGE = "script_language";

    /**
     * The property containing the scope.
     */
    public static final String CP_SCOPE = "scope";

    /**
     * Default constructor.
     */
    public ScriptComponentClassInitializer()
    {
        super(CLASS_REFERENCE);
    }

    @Override
    protected void createClass(BaseClass xclass)
    {
        xclass.addStaticListField(CP_LANGUAGE, "Language", "groovy|python");
        xclass.addStaticListField(CP_SCOPE, "Scope", "|wiki|global|user");
        xclass.addTextAreaField(CP_SCRIPT, "Script", 40, 20, TextAreaClass.EditorType.TEXT);
    }
}
