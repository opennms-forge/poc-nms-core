/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2008-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.provision;


/**
 * <p>DetectFuture interface.</p>
 *
 * @author thedesloge
 * @version $Id: $
 */
public interface DetectFuture {
    
    /**
     * <p>getServiceDetector</p>
     *
     * @return a {@link org.opennms.netmgt.provision.AsyncServiceDetector} object.
     */
    AsyncServiceDetector getServiceDetector();
    
    /**
     * <p>isServiceDetected</p>
     *
     * @return a boolean.
     */
    boolean isServiceDetected();
    
    /**
     * <p>getException</p>
     *
     * @return a {@link java.lang.Throwable} object.
     */
    Throwable getException();
    
    /**
     * <p>setServiceDetected</p>
     *
     * @param serviceDetected a boolean.
     */
    void setServiceDetected(boolean serviceDetected);
    
    /**
     * <p>setException</p>
     *
     * @param throwable a {@link java.lang.Throwable} object.
     */
    void setException(Throwable throwable);
    
    /**
     * <p>awaitFor</p>
     * 
     * @throws InterruptedException 
     */
    void awaitFor() throws InterruptedException;
    
    /**
     * <p>awaitForUninterruptibly</p>
     */
    void awaitForUninterruptibly();
    
    /**
     * <p>isDone</p>
     */
    boolean isDone();
    
    /**
     * <p>addListener</p>
     */
    public DetectFuture addListener(DetectFutureListener<DetectFuture> listener);
}
