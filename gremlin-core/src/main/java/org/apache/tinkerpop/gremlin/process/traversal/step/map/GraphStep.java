/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.process.traversal.step.map;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalEngine;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.EngineDependent;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.AbstractStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.util.iterator.EmptyIterator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Pieter Martin
 */
public class GraphStep<S, E extends Element> extends AbstractStep<S, E> implements EngineDependent {

    protected final Class<E> returnClass;
    protected Object[] ids;
    protected transient Supplier<Iterator<E>> iteratorSupplier;
    protected boolean isStart;
    protected boolean done = false;
    private Traverser.Admin<S> head = null;
    private Iterator<E> iterator = EmptyIterator.instance();


    public GraphStep(final Traversal.Admin traversal, final Class<E> returnClass, final boolean isStart, final Object... ids) {
        super(traversal);
        this.returnClass = returnClass;
        this.ids = (ids.length == 1 && ids[0] instanceof Collection) ? ((Collection) ids[0]).toArray(new Object[((Collection) ids[0]).size()]) : ids;
        this.isStart = isStart;
        this.iteratorSupplier = () -> (Iterator<E>) (Vertex.class.isAssignableFrom(this.returnClass) ?
                this.getTraversal().getGraph().get().vertices(this.ids) :
                this.getTraversal().getGraph().get().edges(this.ids));
    }

    public String toString() {
        return StringFactory.stepString(this, Arrays.toString(this.ids), this.returnClass.getSimpleName().toLowerCase());
    }

    public Class<E> getReturnClass() {
        return this.returnClass;
    }

    public boolean isStartStep() {
        return this.isStart;
    }

    public static boolean isStartStep(final Step<?, ?> step) {
        return step instanceof GraphStep && ((GraphStep) step).isStartStep();
    }

    public boolean returnsVertex() {
        return this.returnClass.equals(Vertex.class);
    }

    public boolean returnsEdge() {
        return this.returnClass.equals(Edge.class);
    }

    public void setIteratorSupplier(final Supplier<Iterator<E>> iteratorSupplier) {
        this.iteratorSupplier = iteratorSupplier;
    }

    public Object[] getIds() {
        return this.ids;
    }

    public void clearIds() {
        this.ids = new Object[0];
    }

    @Override
    public void onEngine(final TraversalEngine traversalEngine) {
        if (traversalEngine.isComputer()) {
            this.iteratorSupplier = Collections::emptyIterator;
            for (int i = 0; i < this.ids.length; i++) {    // if this is going to OLAP, convert to ids so you don't serialize elements
                if (this.ids[i] instanceof Element)
                    this.ids[i] = ((Element) this.ids[i]).id();
            }
        }
    }

    @Override
    protected Traverser<E> processNextStart() {
        while (true) {
            if (this.iterator.hasNext()) {
                return this.isStart ? this.getTraversal().getTraverserGenerator().generate(this.iterator.next(), (Step) this, 1l) : this.head.split(this.iterator.next(), this);
            } else {
                if (this.isStart) {
                    if (this.done)
                        throw FastNoSuchElementException.instance();
                    else {
                        this.done = true;
                        this.iterator = null == this.iteratorSupplier ? EmptyIterator.instance() : this.iteratorSupplier.get();
                    }
                } else {
                    this.head = this.starts.next();
                    this.iterator = null == this.iteratorSupplier ? EmptyIterator.instance() : this.iteratorSupplier.get();
                }
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        this.head = null;
        this.iterator = EmptyIterator.instance();
    }


    @Override
    public int hashCode() {
        int result = super.hashCode() ^ this.returnClass.hashCode();
        for (final Object id : this.ids) {
            result ^= id.hashCode();
        }
        return result;
    }
}
