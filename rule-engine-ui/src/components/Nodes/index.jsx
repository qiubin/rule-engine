import React from 'react'
import { Handle, Position } from 'reactflow'
import './node-styles.css'

// 开始节点
function StartNode({ data }) {
  return (
    <div className="node start-node">
      <div className="node-content">{data.label}</div>
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

// 条件节点
function ConditionNode({ data }) {
  return (
    <div className="node condition-node">
      <Handle type="target" position={Position.Left} />
      <div className="node-header">条件</div>
      <div className="node-content">
        <div className="node-title">{data.label}</div>
        {data.conditionConfig && (
          <div className="node-detail">
            {data.conditionConfig.field} {data.conditionConfig.operator} {data.conditionConfig.value}
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Right} id="true" style={{ top: '30%' }} />
      <Handle type="source" position={Position.Right} id="false" style={{ top: '70%' }} />
    </div>
  )
}

// 结果节点
function ResultNode({ data }) {
  return (
    <div className="node result-node">
      <Handle type="target" position={Position.Left} />
      <div className="node-header">结果</div>
      <div className="node-content">
        <div className="node-title">{data.label}</div>
        {data.resultConfig && (
          <div className="node-detail">
            {data.resultConfig.resultType}: {data.resultConfig.resultValue}
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

// AND 节点
function AndNode({ data }) {
  return (
    <div className="node logic-node">
      <Handle type="target" position={Position.Left} />
      <div className="node-content">AND</div>
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

// OR 节点
function OrNode({ data }) {
  return (
    <div className="node logic-node">
      <Handle type="target" position={Position.Left} />
      <div className="node-content">OR</div>
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

// 结束节点
function EndNode({ data }) {
  return (
    <div className="node end-node">
      <Handle type="target" position={Position.Left} />
      <div className="node-content">{data.label}</div>
    </div>
  )
}

export const nodeTypes = {
  start: StartNode,
  condition: ConditionNode,
  result: ResultNode,
  and: AndNode,
  or: OrNode,
  end: EndNode,
}
