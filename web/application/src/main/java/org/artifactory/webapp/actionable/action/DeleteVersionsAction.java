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

package org.artifactory.webapp.actionable.action;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.artifactory.api.fs.DeployableUnit;
import org.artifactory.api.fs.ItemInfo;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.wicket.component.modal.panel.bordered.BorderedModalPanel;
import org.artifactory.webapp.actionable.RepoAwareActionableItem;
import org.artifactory.webapp.actionable.event.ItemEventTargetComponents;
import org.artifactory.webapp.actionable.event.RepoAwareItemEvent;
import org.artifactory.webapp.wicket.page.browse.treebrowser.TreeBrowsePanel;
import org.artifactory.webapp.wicket.page.browse.treebrowser.action.DeleteVersionsPanel;

import java.util.List;

/**
 * @author Yossi Shaul
 */
public class DeleteVersionsAction extends RepoAwareItemAction {
    public static final String ACTION_NAME = "Delete Versions...";

    public DeleteVersionsAction() {
        super(ACTION_NAME);
    }

    @Override
    public void onAction(RepoAwareItemEvent event) {
        RepoAwareActionableItem source = event.getSource();
        ItemInfo info = source.getItemInfo();
        RepositoryService repositoryService = getRepoService();
        List<DeployableUnit> deployableUnits = repositoryService.getDeployableUnitsUnder(info.getRepoPath());

        ItemEventTargetComponents eventTargetComponents = event.getTargetComponents();
        ModalWindow modalWindow = eventTargetComponents.getModalWindow();
        WebMarkupContainer nodaPanelContainer = eventTargetComponents.getNodePanelContainer();
        TreeBrowsePanel browseRepoPanel = (TreeBrowsePanel) nodaPanelContainer.getParent();

        DeleteVersionsPanel panel = new DeleteVersionsPanel(modalWindow.getContentId(), deployableUnits,
                browseRepoPanel, event.getSource());
        BorderedModalPanel modelPanel = new BorderedModalPanel(panel);
        modelPanel.setTitle("Delete Versions");
        modalWindow.setContent(modelPanel);
        AjaxRequestTarget target = event.getTarget();
        modalWindow.show(target);
    }
}