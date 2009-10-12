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

package org.artifactory.webapp.wicket.page.search.archive;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.Page;
import org.apache.wicket.Session;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.artifactory.api.search.SearchResults;
import org.artifactory.api.search.archive.ArchiveSearchControls;
import org.artifactory.api.search.archive.ArchiveSearchResult;
import org.artifactory.common.wicket.component.checkbox.styled.StyledCheckbox;
import org.artifactory.common.wicket.component.help.HelpBubble;
import org.artifactory.common.wicket.component.table.groupable.column.GroupableColumn;
import org.artifactory.webapp.wicket.actionable.column.ActionsColumn;
import org.artifactory.webapp.wicket.page.search.BaseSearchPage;
import org.artifactory.webapp.wicket.page.search.BaseSearchPanel;
import org.artifactory.webapp.wicket.page.search.actionable.ActionableArchiveSearchResult;
import org.artifactory.webapp.wicket.page.search.actionable.ActionableSearchResult;
import org.artifactory.webapp.wicket.util.validation.ArchiveSearchValidator;

import java.util.List;

/**
 * Displays the archive content searcher
 *
 * @author Noam Tenne
 */
public class ArchiveSearchPanel extends BaseSearchPanel<ArchiveSearchResult> {
    private ArchiveSearchControls searchControls;

    public ArchiveSearchPanel(Page parent, String id) {
        super(parent, id);
    }

    @Override
    protected void addSearchComponents(Form form) {
        searchControls = new ArchiveSearchControls();
        getDataProvider().setGroupParam(new SortParam("searchResult.entry", true));

        TextField searchControl = new TextField("query", new PropertyModel(searchControls, "query"));
        searchControl.setOutputMarkupId(true);
        searchControl.add(new ArchiveSearchValidator());
        form.add(searchControl);

        StyledCheckbox exactMatchCheckbox =
                new StyledCheckbox("exactMatch", new PropertyModel(searchControls, "exactMatch"));
        exactMatchCheckbox.setLabel(new Model("Exact Match"));
        exactMatchCheckbox.setPersistent(true);
        form.add(exactMatchCheckbox);

        form.add(new StyledCheckbox("classSearch", new PropertyModel(searchControls, "searchAllTypes")));
        form.add(new HelpBubble("searchContentHelp", "Search through all types of files (including package names).\n" +
                "Be aware: supplying a search term which may be to common, might not display the complete list of\n" +
                "results, since there may be too many."));

        form.add(new HelpBubble("searchHelp", "Entry name"));
    }

    @Override
    protected Class<? extends BaseSearchPage> getMenuPageClass() {
        return ArchiveSearchPage.class;
    }

    @Override
    protected void onNoResults() {
        String searchQuery = StringEscapeUtils.escapeHtml(searchControls.getQuery());
        if (StringUtils.isEmpty(searchQuery)) {
            searchQuery = "";
        } else {
            searchQuery = " for '" + searchQuery + "'";
        }
        Session.get().warn(String.format("No artifacts found%s.", searchQuery));
    }

    @Override
    protected ActionableSearchResult<ArchiveSearchResult> getActionableResult(
            ArchiveSearchResult searchResult) {
        return new ActionableArchiveSearchResult(searchResult);
    }

    @Override
    protected boolean isLimitSearchResults() {
        return searchControls.isLimitSearchResults();
    }

    @Override
    public String getSearchExpression() {
        return searchControls.getQuery();
    }

    @Override
    protected void addColumns(List<IColumn> columns) {
        columns.add(new ActionsColumn(""));

        columns.add(new GroupableColumn(new Model("Entry Name"), "searchResult.entry", "searchResult.entryPath"));
        columns.add(new BaseSearchPanel.ArtifactNameColumn());
        columns.add(
                new PropertyColumn(new Model("Artifact Path"), "searchResult.relDirPath", "searchResult.relDirPath"));
        columns.add(new PropertyColumn(new Model("Repository"), "searchResult.repoKey", "searchResult.repoKey"));
    }

    @Override
    protected SearchResults<ArchiveSearchResult> searchArtifacts() {
        return search(searchControls);
    }

    @Override
    protected SearchResults<ArchiveSearchResult> performLimitlessArtifactSearch() {
        ArchiveSearchControls controlsCopy = new ArchiveSearchControls(searchControls);
        controlsCopy.setLimitSearchResults(false);
        controlsCopy.setShouldCalcEntries(false);
        return search(controlsCopy);
    }

    /**
     * Performs the search
     *
     * @param controls Search controls
     * @return List of search results
     */
    private SearchResults<ArchiveSearchResult> search(ArchiveSearchControls controls) {
        return searchService.searchArchiveContent(controls);
    }
}