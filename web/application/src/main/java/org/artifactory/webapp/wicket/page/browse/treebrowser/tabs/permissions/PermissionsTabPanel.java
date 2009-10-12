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

package org.artifactory.webapp.wicket.page.browse.treebrowser.tabs.permissions;

import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.artifactory.api.fs.ItemInfo;
import org.artifactory.api.repo.RepoPath;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.wicket.behavior.CssClass;
import org.artifactory.common.wicket.component.table.SortableTable;
import org.artifactory.common.wicket.component.table.columns.BooleanColumn;
import org.artifactory.webapp.actionable.RepoAwareActionableItem;
import org.artifactory.webapp.actionable.model.FolderActionableItem;
import org.artifactory.webapp.wicket.page.security.acl.AceInfoRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays file statistics information.
 *
 * @author Yossi Shaul
 */
public class PermissionsTabPanel extends Panel {
    @SpringBean
    private AuthorizationService authService;

    @SpringBean
    private UserGroupService userGroupService;

    private RepoPath repoPath;

    public PermissionsTabPanel(String id, RepoAwareActionableItem item) {
        super(id);

        ItemInfo itemInfo = item.getItemInfo();
        if ((itemInfo.isFolder()) && (item instanceof FolderActionableItem)) {
            repoPath = ((FolderActionableItem) item).getCanonicalPath();
        } else {
            repoPath = item.getRepoPath();
        }

        addTable();
    }

    private void addTable() {
        List<IColumn> columns = new ArrayList<IColumn>();

        columns.add(new PropertyColumn(new Model("Principal"), "principal", "principal") {
            @Override
            public void populateItem(Item item, String componentId, IModel model) {
                super.populateItem(item, componentId, model);
                AceInfoRow aceInfoRow = (AceInfoRow) model.getObject();
                if (aceInfoRow.isGroup()) {
                    item.add(new CssClass("group"));
                } else {
                    item.add(new CssClass("user"));
                }
            }
        });

        columns.add(new BooleanColumn(new Model("Delete"), "delete", "delete"));
        columns.add(new BooleanColumn(new Model("Deploy"), "deploy", "deploy"));
        columns.add(new BooleanColumn(new Model("Read"), "read", "read"));

        PermissionsTabTableDataProvider dataProvider =
                new PermissionsTabTableDataProvider(userGroupService, authService, repoPath);

        add(new SortableTable("recipients", columns, dataProvider, 10));
    }
}