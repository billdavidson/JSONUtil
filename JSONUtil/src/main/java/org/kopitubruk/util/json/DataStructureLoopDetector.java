/*
 * Copyright 2016 Bill Davidson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kopitubruk.util.json;

import java.util.List;

/**
 * Encapsulates the data structure loop detection from appendRecursiblePropertyValue()
 *
 * @author Bill Davidson
 * @since 1.7.1
 */
class DataStructureLoopDetector
{
    private JSONConfig cfg;
    private Object propertyValue;
    private List<Object> objStack;
    private int stackIndex;

    /**
     * Make a DataStructureLoopDetector
     *
     * @param cfg The config object.
     * @param propertyValue The property value being checked.
     */
    DataStructureLoopDetector( JSONConfig cfg, Object propertyValue )
    {
        this.cfg = cfg;
        this.propertyValue = propertyValue;
        objStack = cfg.getObjStack();
        for ( Object o : objStack ){
            // reference comparison.
            if ( o == propertyValue ){
                throw new DataStructureLoopException(propertyValue, cfg);
            }
        }
        stackIndex = objStack.size();
        objStack.add(propertyValue);
    }

    /**
     * Pop the data structure loop detection stack unless there's an error,
     * in which case throws an exception.
     */
    void popDataStructureStack()
    {
        // remove this value from the stack.
        if ( objStack.size() == (stackIndex+1) && objStack.get(stackIndex) == propertyValue ){
            // current propertyValue is the last value in the list.
            objStack.remove(stackIndex);
        }else{
            // this should never happen.
            throw new LoopDetectionFailureException(stackIndex, cfg);
        }
    }
}
