/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



/**
 * This class is the super class of all the nodes describing a EMD query in
 * the form of a parsed tree
 */

package com.sun.honeycomb.emd.parsers;

import com.sun.honeycomb.common.*;
import java.util.*;

public abstract class QueryNode {
    protected static final int TYPE_INVALID     =0;
    protected static final int TYPE_AND         =1;
    protected static final int TYPE_OR          =2;
    protected static final int TYPE_EXPRESSION  =3;
    protected static final int TYPE_ATTRIBUTE   =4;
    protected static final int TYPE_LITERAL     =5;
    protected static final int TYPE_TEXT        =6;
    protected static final int TYPE_PASS_THROUGH =7;
    protected static final int TYPE_PARAMETER   =8;

    private int type;
    private QueryNode leftChild;
    private QueryNode rightChild;

    protected QueryNode(int newType,
                        QueryNode newLeftChild,
                        QueryNode newRightChild) {
        type = newType;
        leftChild = newLeftChild;
        rightChild = newRightChild;
    }

    protected int getType() {
        return(type);
    }

    public QueryNode getLeftChild() {
        return(leftChild);
    }

    public QueryNode getRightChild() {
        return(rightChild);
    }


    protected static QueryNode multipleOperands(QueryNode[] operands,
                                                int operatorType) {
        ArrayList list = new ArrayList(operands.length);
        
        for (int i=0; i<operands.length; i++) {
            list.add(operands[i]);
        }

        return(multipleOperands(list, operatorType));
    }

    protected static QueryNode multipleOperands(ArrayList operands,
                                                int operatorType) {
        while (operands.size() > 1) {
            QueryNode node1 = (QueryNode)operands.remove(0);
            QueryNode node2 = (QueryNode)operands.remove(0);
            
            switch (operatorType) {
            case TYPE_AND:
                operands.add(0,new QueryAnd(node1, node2));
                break;

            case TYPE_OR:
                operands.add(0,new QueryOr(node1, node2));
                break;

            case TYPE_TEXT:
                operands.add(0,new QueryText(node1, node2));
                break;
            }
        }

        QueryNode result = (QueryNode)operands.get(0);
        return(result);
    }
    
    public abstract String toSQLString() throws NoSuchElementException;

    public abstract String toString();
}
