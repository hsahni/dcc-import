/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.imports.core.util;

import static java.lang.String.format;
import static lombok.AccessLevel.PRIVATE;

import com.mongodb.MongoClientURI;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class Importers {

  /**
   * Constants.
   */
  private static final int DEFAULT_MONGO_PORT = 27017;
  private static final String MONGO_URI_TEMPLATE = "mongodb://localhost:%d/%s";

  /**
   * Local MongoDB URIs.
   */

  public static final MongoClientURI getLocalMongoClientUri(String db) {
    return new MongoClientURI(format(MONGO_URI_TEMPLATE, DEFAULT_MONGO_PORT, db));
  }

  public static final MongoClientURI getLocalMongoClientUri(int port, String db) {
    return new MongoClientURI(format(MONGO_URI_TEMPLATE, port, db));
  }

}
