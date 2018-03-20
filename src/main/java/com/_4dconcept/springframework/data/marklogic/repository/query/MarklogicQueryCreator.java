/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com._4dconcept.springframework.data.marklogic.repository.query;

import com._4dconcept.springframework.data.marklogic.core.mapping.MarklogicPersistentProperty;
import com._4dconcept.springframework.data.marklogic.core.query.Criteria;
import com._4dconcept.springframework.data.marklogic.core.query.CriteriaDefinition;
import com._4dconcept.springframework.data.marklogic.core.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TypeMismatchDataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.context.PersistentPropertyPath;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.Nullable;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom query creator to create Marklogic criterias.
 *
 * @author stoussaint
 * @since 2017-08-03
 */
public class MarklogicQueryCreator extends AbstractQueryCreator<Query, Criteria> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarklogicQueryCreator.class);

    private MappingContext<?, MarklogicPersistentProperty> context;

    MarklogicQueryCreator(PartTree tree, ParameterAccessor parameters, MappingContext<?, MarklogicPersistentProperty> context) {
        super(tree, parameters);

        this.context = context;
    }

    @Override
    protected Criteria create(Part part, Iterator<Object> iterator) {
        PersistentPropertyPath<MarklogicPersistentProperty> path = context.getPersistentPropertyPath(part.getProperty());
        MarklogicPersistentProperty property = path.getLeafProperty();

        if (property == null) {
            throw new TypeMismatchDataAccessException(String.format("No persistent pntity information found for the path %s", path));
        }

        return from(part, property, iterator);
    }

    @Override
    protected Criteria and(Part part, Criteria base, Iterator<Object> iterator) {
        Criteria newCriteria = create(part, iterator);
        if (! Criteria.Operator.and.equals(base.getOperator())) {
            return new Criteria(Criteria.Operator.and, new ArrayList<>(Arrays.asList(base, newCriteria)));
        }

        base.add(newCriteria);
        return base;
    }

    @Override
    protected Criteria or(Criteria base, Criteria criteria) {
        if (! Criteria.Operator.or.equals(base.getOperator())) {
            return new Criteria(Criteria.Operator.or, new ArrayList<>(Arrays.asList(base, criteria)));
        }

        base.add(criteria);
        return base;
    }

    @Override
    protected Query complete(@Nullable Criteria criteria, Sort sort) {
        Query query = criteria == null ? new Query() : new Query(criteria);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Created query " + query);
        }

        return query;
    }

    /**
     * Populates the given {@link CriteriaDefinition} depending on the {@link Part} given.
     *
     * @param part the current method part
     * @param property the resolve entity property
     * @param parameters the method parameters iterator
     * @return the build criteria
     */
    private Criteria from(Part part, MarklogicPersistentProperty property, Iterator<Object> parameters) {

        Part.Type type = part.getType();

        switch (type) {
//            case AFTER:
//            case GREATER_THAN:
//            case GREATER_THAN_EQUAL:
//            case BEFORE:
//            case LESS_THAN:
//            case LESS_THAN_EQUAL:
//            case BETWEEN:
//            case IS_NOT_NULL:
//            case IS_NULL:
//            case NOT_IN:
//            case LIKE:
//            case STARTING_WITH:
//            case ENDING_WITH:
            case IN:
            case CONTAINING:
                return computeContainingCriteria(property.getQName(), parameters.next());
//            case NOT_CONTAINING:
//            case REGEX:
//            case EXISTS:
            case TRUE:
                return computeSimpleCriteria(property.getQName(), true);
            case FALSE:
                return computeSimpleCriteria(property.getQName(), false);
//            case NEAR:
//            case WITHIN:
//            case NEGATING_SIMPLE_PROPERTY:
            case SIMPLE_PROPERTY:
                return computeSimpleCriteria(property.getQName(), parameters.next());
            default:
                throw new IllegalArgumentException("Unsupported keyword : " + type);
        }
    }

    private Criteria computeContainingCriteria(QName qName, Object parameter) {
        if (parameter instanceof List) {
            List<?> list = (List<?>) parameter;
            List<Criteria> criteriaList = list.stream().map(o -> new Criteria(qName, o)).collect(Collectors.toList());
            return new Criteria(Criteria.Operator.or, criteriaList);
        } else {
            return new Criteria(qName, parameter);
        }
    }

    private Criteria computeSimpleCriteria(QName qName, Object parameter) {
        if (parameter instanceof List) {
            List<?> list = (List<?>) parameter;
            List<Criteria> criteriaList = list.stream().map(o -> new Criteria(qName, o)).collect(Collectors.toList());
            return new Criteria(Criteria.Operator.and, criteriaList);
        } else {
            return new Criteria(qName, parameter);
        }
    }

}
