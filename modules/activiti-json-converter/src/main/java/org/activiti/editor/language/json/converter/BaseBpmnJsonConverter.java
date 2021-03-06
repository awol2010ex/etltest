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
package org.activiti.editor.language.json.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.activiti.bpmn.model.ActivitiListener;
import org.activiti.bpmn.model.Activity;
import org.activiti.bpmn.model.BaseElement;
import org.activiti.bpmn.model.BoundaryEvent;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.ErrorEventDefinition;
import org.activiti.bpmn.model.Event;
import org.activiti.bpmn.model.EventDefinition;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FormProperty;
import org.activiti.bpmn.model.GraphicInfo;
import org.activiti.bpmn.model.ImplementationType;
import org.activiti.bpmn.model.MessageEventDefinition;
import org.activiti.bpmn.model.MultiInstanceLoopCharacteristics;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.SignalEventDefinition;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.SubProcess;
import org.activiti.bpmn.model.TimerEventDefinition;
import org.activiti.bpmn.model.UserTask;
import org.activiti.editor.constants.EditorJsonConstants;
import org.activiti.editor.constants.StencilConstants;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

/**
 * @author Tijs Rademakers
 */
public abstract class BaseBpmnJsonConverter implements EditorJsonConstants, StencilConstants {
  
  protected static final Logger LOGGER = Logger.getLogger(BaseBpmnJsonConverter.class.getName());
  
  protected ObjectMapper objectMapper = new ObjectMapper();
  protected ActivityProcessor processor;
  protected BpmnModel model;
  protected Process process;
  protected ObjectNode flowElementNode;
  protected double subProcessX;
  protected double subProcessY;
  protected ArrayNode shapesArrayNode;

  public void convertToJson(FlowElement flowElement, ActivityProcessor processor, Process process, BpmnModel model,
      ArrayNode shapesArrayNode, double subProcessX, double subProcessY) {
    
    this.model = model;
    this.processor = processor;
    this.process = process;
    this.subProcessX = subProcessX;
    this.subProcessY = subProcessY;
    this.shapesArrayNode = shapesArrayNode;
    GraphicInfo graphicInfo = model.getGraphicInfo(flowElement.getId());
    flowElementNode = BpmnJsonConverterUtil.createChildShape(flowElement.getId(), getStencilId(flowElement), 
        graphicInfo.x - subProcessX + graphicInfo.width, 
        graphicInfo.y - subProcessY + graphicInfo.height, 
        graphicInfo.x - subProcessX, graphicInfo.y - subProcessY);
    shapesArrayNode.add(flowElementNode);
    ObjectNode propertiesNode = objectMapper.createObjectNode();
    propertiesNode.put(PROPERTY_OVERRIDE_ID, flowElement.getId());
    if (StringUtils.isNotEmpty(flowElement.getName())) {
      propertiesNode.put(PROPERTY_NAME, flowElement.getName());
    }
    
    if (StringUtils.isNotEmpty(flowElement.getDocumentation())) {
      propertiesNode.put(PROPERTY_DOCUMENTATION, flowElement.getDocumentation());
    }
    
    convertElementToJson(propertiesNode, flowElement);
    
    flowElementNode.put(EDITOR_SHAPE_PROPERTIES, propertiesNode);
    ArrayNode outgoingArrayNode = objectMapper.createArrayNode();
    for (SequenceFlow sequenceFlow : flowElement.getOutgoingFlows()) {
      outgoingArrayNode.add(BpmnJsonConverterUtil.createResourceNode(sequenceFlow.getId()));
    }
    
    if (flowElement instanceof Activity) {
      
      Activity activity = (Activity) flowElement;
      for (BoundaryEvent boundaryEvent : activity.getBoundaryEvents()) {
        outgoingArrayNode.add(BpmnJsonConverterUtil.createResourceNode(boundaryEvent.getId()));
      }
      
      if (activity.isAsynchronous()) {
        propertiesNode.put(PROPERTY_ASYNCHRONOUS, PROPERTY_VALUE_YES);
      }
      
      if (activity.isNotExclusive()) {
        propertiesNode.put(PROPERTY_EXCLUSIVE, PROPERTY_VALUE_NO);
      }
      
      if (activity.getLoopCharacteristics() != null) {
        MultiInstanceLoopCharacteristics loopDef = activity.getLoopCharacteristics();
        if (StringUtils.isNotEmpty(loopDef.getLoopCardinality()) || StringUtils.isNotEmpty(loopDef.getInputDataItem()) ||
            StringUtils.isNotEmpty(loopDef.getCompletionCondition())) {
          
          if (loopDef.isSequential() == false) {
            propertiesNode.put(PROPERTY_MULTIINSTANCE_SEQUENTIAL, PROPERTY_VALUE_NO);
          }
          if (StringUtils.isNotEmpty(loopDef.getLoopCardinality())) {
            propertiesNode.put(PROPERTY_MULTIINSTANCE_CARDINALITY, loopDef.getLoopCardinality());
          }
          if (StringUtils.isNotEmpty(loopDef.getInputDataItem())) {
            propertiesNode.put(PROPERTY_MULTIINSTANCE_COLLECTION, loopDef.getInputDataItem());
          }
          if (StringUtils.isNotEmpty(loopDef.getElementVariable())) {
            propertiesNode.put(PROPERTY_MULTIINSTANCE_VARIABLE, loopDef.getElementVariable());
          }
          if (StringUtils.isNotEmpty(loopDef.getCompletionCondition())) {
            propertiesNode.put(PROPERTY_MULTIINSTANCE_CONDITION, loopDef.getCompletionCondition());
          }
        }
      }
    }
    
    flowElementNode.put("outgoing", outgoingArrayNode);
  }
  
  public void convertToBpmnModel(JsonNode elementNode, JsonNode modelNode, 
      ActivityProcessor processor, BaseElement parentElement, Map<String, JsonNode> shapeMap) {
    
    this.processor = processor;
    
    FlowElement flowElement = convertJsonToElement(elementNode, modelNode, shapeMap);
    flowElement.setId(BpmnJsonConverterUtil.getElementId(elementNode));
    flowElement.setName(getPropertyValueAsString(PROPERTY_NAME, elementNode));
    flowElement.setDocumentation(getPropertyValueAsString(PROPERTY_DOCUMENTATION, elementNode));
    
    convertJsonToListeners(elementNode, flowElement);
    
    if (flowElement instanceof Activity) {
      Activity activity = (Activity) flowElement;
      activity.setAsynchronous(getPropertyValueAsBoolean(PROPERTY_ASYNCHRONOUS, elementNode));
      activity.setNotExclusive(getPropertyValueAsBoolean(PROPERTY_EXCLUSIVE, elementNode));
      
      String multiInstanceCardinality = getPropertyValueAsString(PROPERTY_MULTIINSTANCE_CARDINALITY, elementNode);
      String multiInstanceCollection = getPropertyValueAsString(PROPERTY_MULTIINSTANCE_COLLECTION, elementNode);
      String multiInstanceCondition = getPropertyValueAsString(PROPERTY_MULTIINSTANCE_CONDITION, elementNode);
      
      if (StringUtils.isNotEmpty(multiInstanceCardinality) || StringUtils.isNotEmpty(multiInstanceCollection) ||
          StringUtils.isNotEmpty(multiInstanceCondition)) {
        
        String multiInstanceVariable = getPropertyValueAsString(PROPERTY_MULTIINSTANCE_VARIABLE, elementNode);
        
        MultiInstanceLoopCharacteristics multiInstanceObject = new MultiInstanceLoopCharacteristics();
        multiInstanceObject.setSequential(getPropertyValueAsBoolean(PROPERTY_MULTIINSTANCE_SEQUENTIAL, elementNode));
        multiInstanceObject.setLoopCardinality(multiInstanceCardinality);
        multiInstanceObject.setInputDataItem(multiInstanceCollection);
        multiInstanceObject.setElementVariable(multiInstanceVariable);
        multiInstanceObject.setCompletionCondition(multiInstanceCondition);
        activity.setLoopCharacteristics(multiInstanceObject);
      }
    }
    if (parentElement instanceof Process) {
      ((Process) parentElement).addFlowElement(flowElement);
    } else if (parentElement instanceof SubProcess) {
      ((SubProcess) parentElement).addFlowElement(flowElement);
    }
  }
  
  protected abstract void convertElementToJson(ObjectNode propertiesNode, FlowElement flowElement);
  
  protected abstract FlowElement convertJsonToElement(JsonNode elementNode, JsonNode modelNode, Map<String, JsonNode> shapeMap);
  
  protected abstract String getStencilId(FlowElement flowElement);
  
  protected void addFormProperties(List<FormProperty> formProperties, ObjectNode propertiesNode) {
    ObjectNode formPropertiesNode = objectMapper.createObjectNode();
    ArrayNode itemsNode = objectMapper.createArrayNode();
    for (FormProperty property : formProperties) {
      ObjectNode propertyItemNode = objectMapper.createObjectNode();
      propertyItemNode.put(PROPERTY_FORM_ID, property.getId());
      propertyItemNode.put(PROPERTY_FORM_NAME, property.getName());
      propertyItemNode.put(PROPERTY_FORM_TYPE, property.getType());
      if (StringUtils.isNotEmpty(property.getExpression())) {
        propertyItemNode.put(PROPERTY_FORM_EXPRESSION, property.getExpression());
      } else {
        propertyItemNode.putNull(PROPERTY_FORM_EXPRESSION);
      }
      if (StringUtils.isNotEmpty(property.getVariable())) {
        propertyItemNode.put(PROPERTY_FORM_VARIABLE, property.getVariable());
      } else {
        propertyItemNode.putNull(PROPERTY_FORM_VARIABLE);
      }
      
      itemsNode.add(propertyItemNode);
    }
    
    formPropertiesNode.put("totalCount", itemsNode.size());
    formPropertiesNode.put(EDITOR_PROPERTIES_GENERAL_ITEMS, itemsNode);
    propertiesNode.put("formproperties", formPropertiesNode);
  }
  
  protected void addEventProperties(Event event, ObjectNode propertiesNode) {
    List<EventDefinition> eventDefinitions = event.getEventDefinitions();
    if (eventDefinitions.size() == 1) {
    
      EventDefinition eventDefinition = eventDefinitions.get(0);
      if (eventDefinition instanceof ErrorEventDefinition) {
        ErrorEventDefinition errorDefinition = (ErrorEventDefinition) eventDefinition;
        if (StringUtils.isNotEmpty(errorDefinition.getErrorCode())) {
          propertiesNode.put(PROPERTY_ERRORREF, errorDefinition.getErrorCode());
        }
        
      } else if (eventDefinition instanceof SignalEventDefinition) {
        SignalEventDefinition signalDefinition = (SignalEventDefinition) eventDefinition;
        if (StringUtils.isNotEmpty(signalDefinition.getSignalRef())) {
          propertiesNode.put(PROPERTY_SIGNALREF, signalDefinition.getSignalRef());
        }
        
      } else if (eventDefinition instanceof TimerEventDefinition) {
        TimerEventDefinition timerDefinition = (TimerEventDefinition) eventDefinition;
        if (StringUtils.isNotEmpty(timerDefinition.getTimeDuration())) {
          propertiesNode.put(PROPERTY_TIMER_DURATON, timerDefinition.getTimeDuration());
        }
        if (StringUtils.isNotEmpty(timerDefinition.getTimeCycle())) {
          propertiesNode.put(PROPERTY_TIMER_CYCLE, timerDefinition.getTimeCycle());
        }
        if (StringUtils.isNotEmpty(timerDefinition.getTimeDate())) {
          propertiesNode.put(PROPERTY_TIMER_DATE, timerDefinition.getTimeDate());
        }
      }
    }
  }
  
  protected void convertJsonToFormProperties(JsonNode objectNode, BaseElement element) {
    
    JsonNode formPropertiesNode = getProperty(PROPERTY_FORM_PROPERTIES, objectNode);
    if (formPropertiesNode != null) {
      JsonNode itemsArrayNode = formPropertiesNode.get(EDITOR_PROPERTIES_GENERAL_ITEMS);
      if (itemsArrayNode != null) {
        for (JsonNode formNode : itemsArrayNode) {
          JsonNode formIdNode = formNode.get(PROPERTY_FORM_ID);
          if (formIdNode != null && StringUtils.isNotEmpty(formIdNode.asText())) {
            
            FormProperty formProperty = new FormProperty();
            formProperty.setId(formIdNode.asText());
            formProperty.setName(getValueAsString(PROPERTY_FORM_NAME, formNode));
            formProperty.setType(getValueAsString(PROPERTY_FORM_TYPE, formNode));
            formProperty.setExpression(getValueAsString(PROPERTY_FORM_EXPRESSION, formNode));
            formProperty.setVariable(getValueAsString(PROPERTY_FORM_VARIABLE, formNode));
            
            if (element instanceof StartEvent) {
              ((StartEvent) element).getFormProperties().add(formProperty);
            } else if (element instanceof UserTask) {
              ((UserTask) element).getFormProperties().add(formProperty);
            }
          }
        }
      }
    }
  }
  
  protected void convertJsonToListeners(JsonNode objectNode, BaseElement element) {
    JsonNode listenersNode = null;
    
    String propertyName = null;
    String eventType = null;
    String listenerClass = null;
    String listenerExpression = null;
    String listenerDelegateExpression = null;
    
    if (element instanceof UserTask) {
      propertyName = PROPERTY_TASK_LISTENERS;
      eventType = PROPERTY_TASK_LISTENER_EVENT;
      listenerClass = PROPERTY_TASK_LISTENER_CLASS;
      listenerExpression = PROPERTY_TASK_LISTENER_EXPRESSION;
      listenerDelegateExpression = PROPERTY_TASK_LISTENER_DELEGATEEXPRESSION;
      
    } else {
      propertyName = PROPERTY_EXECUTION_LISTENERS;
      eventType = PROPERTY_EXECUTION_LISTENER_EVENT;
      listenerClass = PROPERTY_EXECUTION_LISTENER_CLASS;
      listenerExpression = PROPERTY_EXECUTION_LISTENER_EXPRESSION;
      listenerDelegateExpression = PROPERTY_EXECUTION_LISTENER_DELEGATEEXPRESSION;
    }
    
    listenersNode = getProperty(propertyName, objectNode);
    
    if (listenersNode != null && StringUtils.isNotEmpty(listenersNode.asText())) {
    
      try {
        listenersNode = objectMapper.readTree(listenersNode.asText());
      } catch (Exception e) {
        LOGGER.log(Level.INFO, "Listeners node can not be read", e);
      }
    
      JsonNode itemsArrayNode = listenersNode.get(EDITOR_PROPERTIES_GENERAL_ITEMS);
      if (itemsArrayNode != null) {
        for (JsonNode itemNode : itemsArrayNode) {
          JsonNode typeNode = itemNode.get(eventType);
          if (typeNode != null && StringUtils.isNotEmpty(typeNode.asText())) {
            
            ActivitiListener listener = new ActivitiListener();
            listener.setEvent(typeNode.asText());
            if (StringUtils.isNotEmpty(getValueAsString(listenerClass, itemNode))) {
              listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
              listener.setImplementation(getValueAsString(listenerClass, itemNode));
            } else if (StringUtils.isNotEmpty(getValueAsString(listenerExpression, itemNode))) {
              listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_EXPRESSION);
              listener.setImplementation(getValueAsString(listenerExpression, itemNode));
            } else if (StringUtils.isNotEmpty(getValueAsString(listenerDelegateExpression, itemNode))) {
              listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
              listener.setImplementation(getValueAsString(listenerDelegateExpression, itemNode));
            }
            
            if (element instanceof Process) {
              ((Process) element).getExecutionListeners().add(listener);
            } else if (element instanceof SequenceFlow) {
              ((SequenceFlow) element).getExecutionListeners().add(listener);
            } else if (element instanceof UserTask) {
              ((UserTask) element).getTaskListeners().add(listener);
            } else if (element instanceof Activity) {
              ((Activity) element).getExecutionListeners().add(listener);
            }
          }
        }
      }
    }
  }
  
  protected void convertJsonToTimerDefinition(JsonNode objectNode, Event event) {
    
    String timeDate = getPropertyValueAsString(PROPERTY_TIMER_DATE, objectNode);
    String timeCycle = getPropertyValueAsString(PROPERTY_TIMER_CYCLE, objectNode);
    String timeDuration = getPropertyValueAsString(PROPERTY_TIMER_DURATON, objectNode);
    
    if (StringUtils.isNotEmpty(timeDate) || StringUtils.isNotEmpty(timeCycle) || StringUtils.isNotEmpty(timeDuration)) {
    
      TimerEventDefinition eventDefinition = new TimerEventDefinition();
      if (StringUtils.isNotEmpty(timeDate)) {
        eventDefinition.setTimeDate(timeDate);
        
      } else if (StringUtils.isNotEmpty(timeCycle)) {
        eventDefinition.setTimeCycle(timeCycle);
        
      } else if (StringUtils.isNotEmpty(timeDuration)) {
        eventDefinition.setTimeDuration(timeDuration);
      }
      
      event.getEventDefinitions().add(eventDefinition);
    }
  }
  
  protected void convertJsonToSignalDefinition(JsonNode objectNode, Event event) {
    String signalRef = getPropertyValueAsString(PROPERTY_SIGNALREF, objectNode);
    
    if (StringUtils.isNotEmpty(signalRef)) {
      SignalEventDefinition eventDefinition = new SignalEventDefinition();
      eventDefinition.setSignalRef(signalRef);
      event.getEventDefinitions().add(eventDefinition);
    }
  }
  
  protected void convertJsonToMessageDefinition(JsonNode objectNode, Event event) {
    String messageRef = getPropertyValueAsString(PROPERTY_MESSAGEREF, objectNode);
    
    if (StringUtils.isNotEmpty(messageRef)) {
      MessageEventDefinition eventDefinition = new MessageEventDefinition();
      eventDefinition.setMessageRef(messageRef);
      event.getEventDefinitions().add(eventDefinition);
    }
  }
  
  protected void convertJsonToErrorDefinition(JsonNode objectNode, Event event) {
    String errorRef = getPropertyValueAsString(PROPERTY_ERRORREF, objectNode);
    
    if (StringUtils.isNotEmpty(errorRef)) {
      ErrorEventDefinition eventDefinition = new ErrorEventDefinition();
      eventDefinition.setErrorCode(errorRef);
      event.getEventDefinitions().add(eventDefinition);
    }
  }
  
  protected String getValueAsString(String name, JsonNode objectNode) {
    String propertyValue = null;
    JsonNode propertyNode = objectNode.get(name);
    if (propertyNode != null) {
      propertyValue = propertyNode.asText();
    }
    return propertyValue;
  }
  
  protected List<String> getValueAsList(String name, JsonNode objectNode) {
    List<String> resultList = new ArrayList<String>();
    String propertyValue = getValueAsString(name, objectNode);
    if (propertyValue != null) {
      String[] valueList = propertyValue.split(",");
      for (String value : valueList) {
        resultList.add(value.trim());
      }
    }
    return resultList;
  }
  
  protected String getPropertyValueAsString(String name, JsonNode objectNode) {
    String propertyValue = null;
    JsonNode propertyNode = getProperty(name, objectNode);
    if (propertyNode != null) {
      propertyValue = propertyNode.asText();
    }
    return propertyValue;
  }
  protected boolean getPropertyValueAsBoolean(String name, JsonNode objectNode) {
    boolean result = false;
    String stringValue = getPropertyValueAsString(name, objectNode);
    if (PROPERTY_VALUE_YES.equalsIgnoreCase(stringValue)) {
      result = true;
    }
    return result;
  }
  
  protected List<String> getPropertyValueAsList(String name, JsonNode objectNode) {
    List<String> resultList = new ArrayList<String>();
    JsonNode propertyNode = getProperty(name, objectNode);
    if (propertyNode != null) {
      String propertyValue = propertyNode.asText();
      String[] valueList = propertyValue.split(",");
      for (String value : valueList) {
        resultList.add(value.trim());
      }
    }
    return resultList;
  }
  
  protected JsonNode getProperty(String name, JsonNode objectNode) {
    JsonNode propertyNode = null;
    if (objectNode.get(EDITOR_SHAPE_PROPERTIES) != null) {
      JsonNode propertiesNode = objectNode.get(EDITOR_SHAPE_PROPERTIES);
      propertyNode = propertiesNode.get(name);
    }
    return propertyNode;
  }
  
  protected String convertListToCommaSeparatedString(List<String> stringList) {
    String resultString = null;
    if (stringList  != null && stringList.size() > 0) {
      StringBuilder expressionBuilder = new StringBuilder();
      for (String singleItem : stringList) {
        if (expressionBuilder.length() > 0) {
          expressionBuilder.append(",");
        } 
        expressionBuilder.append(singleItem);
      }
      resultString = expressionBuilder.toString();
    }
    return resultString;
  }
}
