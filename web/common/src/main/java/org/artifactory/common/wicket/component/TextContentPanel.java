/*
 * This file is part of Artifactory.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.artifactory.common.wicket.component;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

/**
 * Created by IntelliJ IDEA. User: yoavl
 */
public class TextContentPanel extends Panel {
    private final Label contentText = new Label("contentText");

    public TextContentPanel(String id) {
        this(id, true);
    }

    public TextContentPanel(String id, boolean escapeMarkup) {
        super(id);
        contentText.setEscapeModelStrings(escapeMarkup);
        add(contentText);
    }

    public Label getContentText() {
        return contentText;
    }

    public String getContent() {
        return (String) contentText.getModelObject();
    }

    public TextContentPanel setContent(String content) {
        contentText.setModel(new Model(content));
        return this;
    }
}