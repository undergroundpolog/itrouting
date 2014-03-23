/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intraffic.org.neo4j.graphalgo;
/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
//package org.neo4j.graphalgo;

//import org.neo4j.graphalgo.CostEvaluator;
import com.intraffic.org.neo4j.graphalgo.CostEvaluator;


//import org.neo4j.graphalgo.impl.path.AStar;

import com.intraffic.org.neo4j.graphalgo.impl.path.AStar;
import org.neo4j.graphalgo.EstimateEvaluator;


import org.neo4j.graphalgo.impl.path.AllPaths;
import org.neo4j.graphalgo.impl.path.AllSimplePaths;
import org.neo4j.graphalgo.impl.path.Dijkstra;
import org.neo4j.graphalgo.impl.path.ShortestPath;
import org.neo4j.graphalgo.impl.util.DoubleEvaluator;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.graphdb.traversal.InitialBranchState;
import org.neo4j.graphdb.traversal.InitialStateFactory;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.WeightedPath;

/**
 * Static factory methods for the recommended implementations of common
 * graph algorithms for Neo4j. The algorithms exposed here are implementations
 * which are tested extensively and also scale on bigger graphs.
 * 
 * @author Mattias Persson
 */
public abstract class GraphAlgoFactory2 extends GraphAlgoFactory
{
    
    
    /**
     * Returns an {@link PathFinder} which uses the A* algorithm to find the
     * cheapest path between two nodes. The definition of "cheap" is the lowest
     * possible cost to get from the start node to the end node, where the cost
     * is returned from {@code lengthEvaluator} and {@code estimateEvaluator}.
     * These returned paths cannot contain loops (i.e. a node cannot occur more
     * than once in any returned path).
     * 
     * See http://en.wikipedia.org/wiki/A*_search_algorithm for more
     * information.
     * 
     * @see AStar
     * @param expander the {@link RelationshipExpander} to use for expanding
     * {@link Relationship}s for each {@link Node}.
     * @param lengthEvaluator evaluator that can return the cost represented
     * by each relationship the algorithm traverses.
     * @param estimateEvaluator evaluator that returns an (optimistic)
     * estimation of the cost to get from the current node (in the traversal)
     * to the end node.
     * @return an algorithm which finds the cheapest path between two nodes
     * using the A* algorithm.
     */
    
    public static PathFinder<WeightedPath> aStar( RelationshipExpander expander,
            CostEvaluator<Double> lengthEvaluator, EstimateEvaluator<Double> estimateEvaluator )
    {
        return new AStar( expander, lengthEvaluator, estimateEvaluator );
    }

    /**
     * Returns an {@link PathFinder} which uses the A* algorithm to find the
     * cheapest path between two nodes. The definition of "cheap" is the lowest
     * possible cost to get from the start node to the end node, where the cost
     * is returned from {@code lengthEvaluator} and {@code estimateEvaluator}.
     * These returned paths cannot contain loops (i.e. a node cannot occur more
     * than once in any returned path).
     * 
     * See http://en.wikipedia.org/wiki/A*_search_algorithm for more
     * information.
     * 
     * @see AStar
     * @param expander the {@link PathExpander} to use for expanding
     * {@link Relationship}s for each {@link Path}.
     * @param lengthEvaluator evaluator that can return the cost represented
     * by each relationship the algorithm traverses.
     * @param estimateEvaluator evaluator that returns an (optimistic)
     * estimation of the cost to get from the current node (in the traversal)
     * to the end node.
     * @return an algorithm which finds the cheapest path between two nodes
     * using the A* algorithm.
     */
    public static PathFinder<WeightedPath> aStar( PathExpander expander,
            CostEvaluator<Double> lengthEvaluator, EstimateEvaluator<Double> estimateEvaluator )
    {
        return new AStar( expander, lengthEvaluator, estimateEvaluator );
    }
    
}
