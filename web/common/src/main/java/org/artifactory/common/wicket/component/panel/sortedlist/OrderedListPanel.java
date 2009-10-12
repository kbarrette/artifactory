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

package org.artifactory.common.wicket.component.panel.sortedlist;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.behavior.IBehavior;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.HiddenField;
import org.apache.wicket.markup.html.link.AbstractLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.artifactory.common.wicket.behavior.CssClass;
import org.artifactory.common.wicket.behavior.JavascriptEvent;
import org.artifactory.common.wicket.component.links.SimpleTitledLink;
import org.artifactory.common.wicket.component.modal.links.ModalShowLink;
import org.artifactory.common.wicket.component.modal.panel.BaseModalPanel;
import org.artifactory.common.wicket.component.panel.titled.TitledPanel;
import org.artifactory.common.wicket.component.table.columns.panel.links.LinksColumnPanel;
import org.artifactory.common.wicket.component.template.HtmlTemplate;
import org.artifactory.common.wicket.contributor.ResourcePackage;
import org.artifactory.common.wicket.resources.basewidget.BaseWidget;
import org.artifactory.log.LoggerFactory;
import org.slf4j.Logger;

import java.io.Serializable;
import static java.lang.String.format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Yoav Aharoni
 */
public abstract class OrderedListPanel<T> extends TitledPanel {
    private static final Logger log = LoggerFactory.getLogger(OrderedListPanel.class);

    protected OrderedListPanel(String id, Collection<T> collection) {
        this(id, new ArrayList<T>(collection));
    }

    protected OrderedListPanel(String id, List<T> list) {
        this(id, new Model((Serializable) list));
    }

    protected OrderedListPanel(String id, IModel listModel) {
        super(id, listModel);
        init();
    }

    protected void init() {
        ResourcePackage resourcePackage = ResourcePackage.forJavaScript(OrderedListPanel.class);
        resourcePackage.dependsOn(new BaseWidget());
        add(resourcePackage);

        add(new CssClass("list-panel ordered-list-panel"));

        // add moveup/down links
        SimpleTitledLink upLink = new SimpleTitledLink("moveUpLink", "Up");
        add(upLink);

        SimpleTitledLink downLink = new SimpleTitledLink("moveDownLink", "Down");
        add(downLink);

        // add new item link
        add(new ModalShowLink("newItemLink", "New") {
            @Override
            protected BaseModalPanel getModelPanel() {
                return newCreateItemPanel();
            }
        });

        // add items container
        WebMarkupContainer container = new WebMarkupContainer("items");
        container.setOutputMarkupId(true);
        container.add(new SimpleAttributeModifier("dojoType", "artifactory.dnd.Source"));
        container.add(new AttributeAppender("accept", true, new DndTypeModel(), ","));
        add(container);

        // add ListView
        ListView listView = new ListView("item", new DelegeteModel()) {
            @Override
            protected void populateItem(ListItem item) {
                OrderedListPanel.this.populateItem(item);
                item.add(new CssClass(item.getIndex() % 2 == 0 ? "even" : "odd"));
                item.add(new JavascriptEvent("onclick", ""));
            }
        };
        container.add(listView);

        // add hidden text field
        HiddenField textField = new IndicesHiddenField("listIndices");
        textField.setOutputMarkupId(true);
        textField.add(newOnOrderChangeEventBehavior("onOrderChanged"));
        add(textField);

        // add init script
        HtmlTemplate template = new HtmlTemplate("initScript");
        template.setParameter("listId", new PropertyModel(container, "markupId"));
        template.setParameter("textFieldId", new PropertyModel(textField, "markupId"));
        template.setParameter("upLinkId", new PropertyModel(upLink, "markupId"));
        template.setParameter("downLinkId", new PropertyModel(downLink, "markupId"));
        add(template);
    }

    @SuppressWarnings({"RefusedBequest"})
    @Override
    public String getTitle() {
        return getString(getId() + ".title", null, getId() + ".title");
    }

    protected abstract String getItemDisplayValue(T itemObject);

    protected abstract BaseModalPanel newCreateItemPanel();

    protected abstract List<? extends AbstractLink> getItemActions(T itemObject, String linkId);

    @SuppressWarnings({"unchecked"})
    protected void populateItem(ListItem item) {
        item.add(new AttributeModifier("dndType", true, new DndTypeModel()));
        item.add(new CssClass("dojoDndItem"));

        T itemObject = (T) item.getModelObject();
        item.add(new Label("name", getItemDisplayValue(itemObject)));

        LinksColumnPanel linksPanel = new LinksColumnPanel("actions");
        item.add(linksPanel);
        List<? extends AbstractLink> links = getItemActions(itemObject, "link");
        for (AbstractLink link : links) {
            linksPanel.addLink(link);
        }
    }

    @SuppressWarnings({"unchecked"})
    public List<T> getList() {
        return (List<T>) getModelObject();
    }

    protected void onOrderChanged(AjaxRequestTarget target) {
        target.appendJavascript(format("dojo.byId('%s')._panel.resetIndices();", get("items").getMarkupId()));
    }

    protected IBehavior newOnOrderChangeEventBehavior(String event) {
        return new OnOrderChangedEventBehavior(event);
    }

    private class DndTypeModel extends AbstractReadOnlyModel {
        @Override
        public Object getObject() {
            return "dnd-" + getMarkupId();
        }
    }

    private class DelegeteModel extends AbstractReadOnlyModel {
        @Override
        public Object getObject() {
            return getModelObject();
        }
    }

    private class IndicesHiddenField extends HiddenField {
        private IndicesHiddenField(String id) {
            super(id, new Model());
        }

        @Override
        public void updateModel() {
            int[] indices = createIndicesList(getConvertedInput());
            if (indices == null) {
                return;
            }

            // reorder list according to indices
            List<T> newList = new ArrayList<T>(indices.length);
            List<T> choices = getList();
            for (int index : indices) {
                newList.add(choices.get(index));
            }
            OrderedListPanel.this.setModelObject(newList);
        }

        /**
         * @param value hidden indices field value
         * @return indides reorder list or null if indices order hasn't changed.
         */
        private int[] createIndicesList(Object value) {
            if (value == null) {
                return null;
            }

            String indicesString = value.toString();
            String[] strings = indicesString.split(",");

            // senity
            if (strings.length != getList().size()) {
                log.error("Indices list size mismatch model list size (reorder ignored).");
                return null;
            }

            // check if order changed and create indices array
            int[] indices = new int[strings.length];
            boolean changed = false;
            for (int i = 0; i < strings.length; i++) {
                String indexString = strings[i];
                Integer index = Integer.valueOf(indexString);
                changed = changed || index != i;
                indices[i] = index;
            }

            if (!changed) {
                return null;
            }
            return indices;
        }

    }

    public class OnOrderChangedEventBehavior extends AjaxFormComponentUpdatingBehavior {
        private OnOrderChangedEventBehavior(String event) {
            super(event);
        }

        @Override
        protected void onUpdate(AjaxRequestTarget target) {
            onOrderChanged(target);
        }
    }
}