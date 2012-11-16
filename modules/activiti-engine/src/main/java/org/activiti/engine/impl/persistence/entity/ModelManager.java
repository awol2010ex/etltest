/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.activiti.engine.impl.persistence.entity;

import java.util.Date;
import java.util.List;

import org.activiti.engine.impl.ModelQueryImpl;
import org.activiti.engine.impl.Page;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.db.DbSqlSession;
import org.activiti.engine.impl.db.PersistentObject;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.AbstractManager;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ModelQuery;


/**
 * @author Tijs Rademakers
 */
public class ModelManager extends AbstractManager {

  public Model createNewModel() {
    return new ModelEntity();
  }

  public void insertModel(Model model) {
    ((ModelEntity) model).setCreateTime(new Date());
    getDbSqlSession().insert((PersistentObject) model);
  }

  public void updateModel(ModelEntity updatedModel) {
    CommandContext commandContext = Context.getCommandContext();
    DbSqlSession dbSqlSession = commandContext.getDbSqlSession();
    dbSqlSession.update(updatedModel);
  }

  public void deleteModel(String modelId) {
    ModelEntity model = getDbSqlSession().selectById(ModelEntity.class, modelId);
    getDbSqlSession().delete(model);
  }

  public ModelQuery createNewModelQuery() {
    return new ModelQueryImpl(Context.getProcessEngineConfiguration().getCommandExecutorTxRequired());
  }

  @SuppressWarnings("unchecked")
  public List<Model> findModelsByQueryCriteria(ModelQueryImpl query, Page page) {
    return getDbSqlSession().selectList("selectModelsByQueryCriteria", query, page);
  }
  
  public long findModelCountByQueryCriteria(ModelQueryImpl query) {
    return (Long) getDbSqlSession().selectOne("selectModelCountByQueryCriteria", query);
  }

  public ModelEntity findModelById(String modelId) {
    return (ModelEntity) getDbSqlSession().selectOne("selectModel", modelId);
  }
  
}