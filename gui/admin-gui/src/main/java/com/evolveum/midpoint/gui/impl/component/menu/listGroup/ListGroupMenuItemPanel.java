/*
 * Copyright (c) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.component.menu.listGroup;

import java.io.Serializable;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;

/**
 * Created by Viliam Repan (lazyman).
 */
public class ListGroupMenuItemPanel<T extends Serializable> extends BasePanel<ListGroupMenuItem<T>> {

    private static final long serialVersionUID = 1L;

    private static final String ID_LINK = "link";
    private static final String ID_ITEMS_CONTAINER = "itemsContainer";
    private static final String ID_ITEMS = "items";
    private static final String ID_ITEM = "item";

    public ListGroupMenuItemPanel(String id, IModel<ListGroupMenuItem<T>> model, int level) {
        super(id, model);

        initLayout(level);
    }

    @Override
    protected void onComponentTag(ComponentTag tag) {
        super.onComponentTag(tag);

        checkComponentTag(tag, "li");
    }

    private void initLayout(int level) {
        add(AttributeAppender.append("class", () -> getModelObject().isOpen() ? "open" : null));

        MenuItemLinkPanel link = new MenuItemLinkPanel(ID_LINK, getModel(), level) {

            @Override
            protected void onClickPerformed(AjaxRequestTarget target, ListGroupMenuItem item) {
                ListGroupMenuItemPanel.this.onClickPerformed(target, item);
            }

            @Override
            protected void onChevronClickPerformed(AjaxRequestTarget target, ListGroupMenuItem item) {
                ListGroupMenuItemPanel.this.onChevronClickPerformed(target, item);
            }
        };
        add(link);

        WebMarkupContainer itemsContainer = new WebMarkupContainer(ID_ITEMS_CONTAINER);
        itemsContainer.add(AttributeAppender.append("style", () -> !getModelObject().isOpen() ? "display: none;" : null));
        itemsContainer.add(new VisibleBehaviour(() -> getModelObject().isOpen() && !getModelObject().isEmpty()));
        add(itemsContainer);

        ListView<ListGroupMenuItem<T>> items = new ListView<>(ID_ITEMS, () -> getModelObject().getItems()) {

            @Override
            protected void populateItem(ListItem<ListGroupMenuItem<T>> item) {
                ListGroupMenuItem dto = item.getModelObject();

                if (dto instanceof CustomListGroupMenuItem) {
                    CustomListGroupMenuItem<T> custom = (CustomListGroupMenuItem) dto;
                    item.add(custom.createMenuItemPanel(
                            ID_ITEM, item.getModel(), (target, i) -> ListGroupMenuItemPanel.this.onClickPerformed(target, i)));
                    return;
                }

                item.add(new ListGroupMenuItemPanel(ID_ITEM, item.getModel(), level + 1) {

                    @Override
                    protected void onClickPerformed(AjaxRequestTarget target, ListGroupMenuItem item) {
                        ListGroupMenuItemPanel.this.onClickPerformed(target, item);
                    }

                    @Override
                    protected void onChevronClickPerformed(AjaxRequestTarget target, ListGroupMenuItem item) {
                        ListGroupMenuItemPanel.this.onChevronClickPerformed(target, item);
                    }
                });
            }
        };
        itemsContainer.add(items);
    }

    protected void onClickPerformed(AjaxRequestTarget target, ListGroupMenuItem item) {

    }

    protected void onChevronClickPerformed(AjaxRequestTarget target, ListGroupMenuItem item) {

    }
}
