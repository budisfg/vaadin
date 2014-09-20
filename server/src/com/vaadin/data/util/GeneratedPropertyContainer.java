/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.data.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gwt.thirdparty.guava.common.collect.Sets;
import com.vaadin.data.Container;
import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.util.filter.UnsupportedFilterException;
import com.vaadin.shared.ui.grid.SortDirection;
import com.vaadin.ui.components.grid.sort.SortOrder;

/**
 * Container supporting generated properties.
 * 
 * @since
 * @author Vaadin Ltd
 */
public class GeneratedPropertyContainer extends AbstractContainer implements
        Container.Indexed, Container.Sortable, Container.Filterable,
        Container.PropertySetChangeNotifier, Container.ItemSetChangeNotifier {

    private final Container.Indexed wrappedContainer;
    private final Map<Object, PropertyValueGenerator<?>> propertyGenerators;
    private final Map<Filter, List<Filter>> activeFilters;
    private Sortable sortableContainer = null;
    private Filterable filterableContainer = null;

    /**
     * Property implementation for generated properties
     */
    protected static class GeneratedProperty<T> implements Property<T> {

        private Item item;
        private Object itemId;
        private Object propertyId;
        private PropertyValueGenerator<T> generator;

        public GeneratedProperty(Item item, Object propertyId, Object itemId,
                PropertyValueGenerator<T> generator) {
            this.item = item;
            this.itemId = itemId;
            this.propertyId = propertyId;
            this.generator = generator;
        }

        @Override
        public T getValue() {
            return generator.getValue(item, itemId, propertyId);
        }

        @Override
        public void setValue(T newValue) throws ReadOnlyException {
            throw new ReadOnlyException("Generated properties are read only");
        }

        @Override
        public Class<? extends T> getType() {
            return generator.getType();
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        @Override
        public void setReadOnly(boolean newStatus) {
            if (newStatus) {
                // No-op
                return;
            }
            throw new UnsupportedOperationException(
                    "Generated properties are read only");
        }
    }

    /**
     * Item implementation for generated properties.
     */
    protected static class GeneratedPropertyItem implements Item {

        private Map<Object, Property<?>> generatedProperties = new HashMap<Object, Property<?>>();
        Item wrappedItem;
        Object itemId;

        protected GeneratedPropertyItem(Object itemId, Item item) {
            this.itemId = itemId;
            wrappedItem = item;
        }

        @Override
        public Property getItemProperty(Object id) {
            if (generatedProperties.containsKey(id)) {
                return generatedProperties.get(id);
            }
            return wrappedItem.getItemProperty(id);
        }

        @Override
        public Collection<?> getItemPropertyIds() {
            return Sets.union(asSet(wrappedItem.getItemPropertyIds()),
                    asSet(generatedProperties.keySet()));
        }

        @Override
        public boolean addItemProperty(Object id, Property property)
                throws UnsupportedOperationException {
            generatedProperties.put(id, property);
            return true;
        }

        @Override
        public boolean removeItemProperty(Object id)
                throws UnsupportedOperationException {
            return generatedProperties.remove(id) != null;
        }
    };

    /**
     * Constructor for GeneratedPropertyContainer.
     * 
     * @param container
     *            underlying indexed container
     */
    public GeneratedPropertyContainer(Container.Indexed container) {
        wrappedContainer = container;
        propertyGenerators = new HashMap<Object, PropertyValueGenerator<?>>();

        if (wrappedContainer instanceof Sortable) {
            sortableContainer = (Sortable) wrappedContainer;
        }

        if (wrappedContainer instanceof Filterable) {
            activeFilters = new HashMap<Filter, List<Filter>>();
            filterableContainer = (Filterable) wrappedContainer;
        } else {
            activeFilters = null;
        }

        // ItemSetChangeEvents
        if (wrappedContainer instanceof ItemSetChangeNotifier) {
            ((ItemSetChangeNotifier) wrappedContainer)
                    .addItemSetChangeListener(new ItemSetChangeListener() {

                        @Override
                        public void containerItemSetChange(
                                ItemSetChangeEvent event) {
                            fireItemSetChange();
                        }
                    });
        }

        // PropertySetChangeEvents
        if (wrappedContainer instanceof PropertySetChangeNotifier) {
            ((PropertySetChangeNotifier) wrappedContainer)
                    .addPropertySetChangeListener(new PropertySetChangeListener() {

                        @Override
                        public void containerPropertySetChange(
                                PropertySetChangeEvent event) {
                            fireContainerPropertySetChange();
                        }
                    });
        }
    }

    /* Functions related to generated properties */

    /**
     * Add a new PropertyValueGenerator with given property id. This will
     * override any existing properties with the same property id. Fires a
     * PropertySetChangeEvent.
     * 
     * @param propertyId
     *            property id
     * @param generator
     *            a property value generator
     */
    public void addGeneratedProperty(Object propertyId,
            PropertyValueGenerator<?> generator) {
        propertyGenerators.put(propertyId, generator);
        fireContainerPropertySetChange();
    }

    /**
     * Removes any possible PropertyValueGenerator with given property id. Fires
     * a PropertySetChangeEvent.
     * 
     * @param propertyId
     *            property id
     */
    public void removeGeneratedProperty(Object propertyId) {
        if (propertyGenerators.containsKey(propertyId)) {
            propertyGenerators.remove(propertyId);
            fireContainerPropertySetChange();
        }
    }

    private Item createGeneratedPropertyItem(final Object itemId,
            final Item item) {
        Item generatedItem = new GeneratedPropertyItem(itemId, item);

        for (Object propertyId : propertyGenerators.keySet()) {
            generatedItem.addItemProperty(
                    propertyId,
                    createProperty(item, propertyId, itemId,
                            propertyGenerators.get(propertyId)));
        }
        return generatedItem;
    }

    private <T> Property<T> createProperty(final Item item,
            final Object propertyId, final Object itemId,
            final PropertyValueGenerator<T> generator) {
        return new GeneratedProperty<T>(item, propertyId, itemId, generator);
    }

    private static <T> Set<T> asSet(Collection<T> collection) {
        if (collection instanceof Set) {
            return (Set<T>) collection;
        } else {
            return new HashSet<T>(collection);
        }
    }

    /* Listener functionality */

    @Override
    public void addItemSetChangeListener(ItemSetChangeListener listener) {
        super.addItemSetChangeListener(listener);
    }

    @Override
    public void addListener(ItemSetChangeListener listener) {
        super.addListener(listener);
    }

    @Override
    public void removeItemSetChangeListener(ItemSetChangeListener listener) {
        super.removeItemSetChangeListener(listener);
    }

    @Override
    public void removeListener(ItemSetChangeListener listener) {
        super.removeListener(listener);
    }

    @Override
    public void addPropertySetChangeListener(PropertySetChangeListener listener) {
        super.addPropertySetChangeListener(listener);
    }

    @Override
    public void addListener(PropertySetChangeListener listener) {
        super.addListener(listener);
    }

    @Override
    public void removePropertySetChangeListener(
            PropertySetChangeListener listener) {
        super.removePropertySetChangeListener(listener);
    }

    @Override
    public void removeListener(PropertySetChangeListener listener) {
        super.removeListener(listener);
    }

    /* Filtering functionality */

    @Override
    public void addContainerFilter(Filter filter)
            throws UnsupportedFilterException {
        if (filterableContainer == null) {
            throw new UnsupportedOperationException(
                    "Wrapped container is not filterable");
        }

        List<Filter> addedFilters = new ArrayList<Filter>();
        for (Entry<?, PropertyValueGenerator<?>> entry : propertyGenerators
                .entrySet()) {
            Object property = entry.getKey();
            if (filter.appliesToProperty(property)) {
                // Have generated property modify filter to fit the original
                // data in the container.
                Filter modifiedFilter = entry.getValue().modifyFilter(filter);
                filterableContainer.addContainerFilter(modifiedFilter);
                // Keep track of added filters
                addedFilters.add(modifiedFilter);
            }
        }

        if (addedFilters.isEmpty()) {
            // No generated property modified this filter, use it as is
            addedFilters.add(filter);
            filterableContainer.addContainerFilter(filter);
        }
        // Map filter to actually added filters
        activeFilters.put(filter, addedFilters);
    }

    @Override
    public void removeContainerFilter(Filter filter) {
        if (filterableContainer == null) {
            throw new UnsupportedOperationException(
                    "Wrapped container is not filterable");
        }

        if (activeFilters.containsKey(filter)) {
            for (Filter f : activeFilters.get(filter)) {
                filterableContainer.removeContainerFilter(f);
            }
            activeFilters.remove(filter);
        }
    }

    @Override
    public void removeAllContainerFilters() {
        if (filterableContainer == null) {
            throw new UnsupportedOperationException(
                    "Wrapped container is not filterable");
        }
        filterableContainer.removeAllContainerFilters();
        activeFilters.clear();
    }

    @Override
    public Collection<Filter> getContainerFilters() {
        if (filterableContainer == null) {
            throw new UnsupportedOperationException(
                    "Wrapped container is not filterable");
        }
        return Collections.unmodifiableSet(activeFilters.keySet());
    }

    /* Sorting functionality */

    @Override
    public void sort(Object[] propertyId, boolean[] ascending) {
        if (sortableContainer == null) {
            new UnsupportedOperationException(
                    "Wrapped container is not Sortable");
        }

        if (propertyId.length == 0) {
            sortableContainer.sort(propertyId, ascending);
            return;
        }

        List<Object> actualSortProperties = new ArrayList<Object>();
        List<Boolean> actualSortDirections = new ArrayList<Boolean>();

        for (int i = 0; i < propertyId.length; ++i) {
            Object property = propertyId[i];
            SortDirection direction;
            boolean isAscending = i < ascending.length ? ascending[i] : true;
            if (isAscending) {
                direction = SortDirection.ASCENDING;
            } else {
                direction = SortDirection.DESCENDING;
            }

            if (propertyGenerators.containsKey(property)) {
                // Sorting by a generated property. Generated property should
                // modify sort orders to work with original properties in the
                // container.
                for (SortOrder s : propertyGenerators.get(property)
                        .getSortProperties(new SortOrder(property, direction))) {
                    actualSortProperties.add(s.getPropertyId());
                    actualSortDirections
                            .add(s.getDirection() == SortDirection.ASCENDING);
                }
            } else {
                actualSortProperties.add(property);
                actualSortDirections.add(isAscending);
            }
        }

        boolean[] actualAscending = new boolean[actualSortDirections.size()];
        for (int i = 0; i < actualAscending.length; ++i) {
            actualAscending[i] = actualSortDirections.get(i);
        }

        sortableContainer.sort(actualSortProperties.toArray(), actualAscending);
    }

    @Override
    public Collection<?> getSortableContainerPropertyIds() {
        if (sortableContainer == null) {
            new UnsupportedOperationException(
                    "Wrapped container is not Sortable");
        }

        Set<Object> sortablePropertySet = new HashSet<Object>(
                sortableContainer.getSortableContainerPropertyIds());
        for (Entry<?, PropertyValueGenerator<?>> entry : propertyGenerators
                .entrySet()) {
            Object property = entry.getKey();
            SortOrder order = new SortOrder(property, SortDirection.ASCENDING);
            if (entry.getValue().getSortProperties(order).length > 0) {
                sortablePropertySet.add(property);
            } else {
                sortablePropertySet.remove(property);
            }
        }

        return sortablePropertySet;
    }

    /* Item related overrides */

    @Override
    public Item addItemAfter(Object previousItemId, Object newItemId)
            throws UnsupportedOperationException {
        Item item = wrappedContainer.addItemAfter(previousItemId, newItemId);
        return createGeneratedPropertyItem(newItemId, item);
    }

    @Override
    public Item addItem(Object itemId) throws UnsupportedOperationException {
        Item item = wrappedContainer.addItem(itemId);
        return createGeneratedPropertyItem(itemId, item);
    }

    @Override
    public Item addItemAt(int index, Object newItemId)
            throws UnsupportedOperationException {
        Item item = wrappedContainer.addItemAt(index, newItemId);
        return createGeneratedPropertyItem(newItemId, item);
    }

    @Override
    public Item getItem(Object itemId) {
        Item item = wrappedContainer.getItem(itemId);
        return createGeneratedPropertyItem(itemId, item);
    }

    /* Property related overrides */

    @SuppressWarnings("rawtypes")
    @Override
    public Property getContainerProperty(Object itemId, Object propertyId) {
        if (propertyGenerators.keySet().contains(propertyId)) {
            return getItem(itemId).getItemProperty(propertyId);
        } else {
            return wrappedContainer.getContainerProperty(itemId, propertyId);
        }
    }

    @Override
    public Collection<?> getContainerPropertyIds() {
        return Sets.union(asSet(wrappedContainer.getContainerPropertyIds()),
                asSet(propertyGenerators.keySet()));
    }

    /* Type related overrides */

    @Override
    public Class<?> getType(Object propertyId) {
        if (propertyGenerators.containsKey(propertyId)) {
            return propertyGenerators.get(propertyId).getType();
        } else {
            return wrappedContainer.getType(propertyId);
        }
    }

    /* Unmodified functions */

    @Override
    public Object nextItemId(Object itemId) {
        return wrappedContainer.nextItemId(itemId);
    }

    @Override
    public Object prevItemId(Object itemId) {
        return wrappedContainer.prevItemId(itemId);
    }

    @Override
    public Object firstItemId() {
        return wrappedContainer.firstItemId();
    }

    @Override
    public Object lastItemId() {
        return wrappedContainer.lastItemId();
    }

    @Override
    public boolean isFirstId(Object itemId) {
        return wrappedContainer.isFirstId(itemId);
    }

    @Override
    public boolean isLastId(Object itemId) {
        return wrappedContainer.isLastId(itemId);
    }

    @Override
    public Object addItemAfter(Object previousItemId)
            throws UnsupportedOperationException {
        return wrappedContainer.addItemAfter(previousItemId);
    }

    @Override
    public Collection<?> getItemIds() {
        return wrappedContainer.getItemIds();
    }

    @Override
    public int size() {
        return wrappedContainer.size();
    }

    @Override
    public boolean containsId(Object itemId) {
        return wrappedContainer.containsId(itemId);
    }

    @Override
    public Object addItem() throws UnsupportedOperationException {
        return wrappedContainer.addItem();
    }

    @Override
    public boolean removeItem(Object itemId)
            throws UnsupportedOperationException {
        return wrappedContainer.removeItem(itemId);
    }

    @Override
    public boolean addContainerProperty(Object propertyId, Class<?> type,
            Object defaultValue) throws UnsupportedOperationException {
        return wrappedContainer.addContainerProperty(propertyId, type,
                defaultValue);
    }

    @Override
    public boolean removeContainerProperty(Object propertyId)
            throws UnsupportedOperationException {
        return wrappedContainer.removeContainerProperty(propertyId);
    }

    @Override
    public boolean removeAllItems() throws UnsupportedOperationException {
        return wrappedContainer.removeAllItems();
    }

    @Override
    public int indexOfId(Object itemId) {
        return wrappedContainer.indexOfId(itemId);
    }

    @Override
    public Object getIdByIndex(int index) {
        return wrappedContainer.getIdByIndex(index);
    }

    @Override
    public List<?> getItemIds(int startIndex, int numberOfItems) {
        return wrappedContainer.getItemIds(startIndex, numberOfItems);
    }

    @Override
    public Object addItemAt(int index) throws UnsupportedOperationException {
        return wrappedContainer.addItemAt(index);
    }
}