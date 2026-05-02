import React from 'react'
import { Button } from 'antd'
import { 
  FilterOutlined, 
  CheckCircleOutlined, 
  ApartmentOutlined,
  NodeIndexOutlined
} from '@ant-design/icons'
import './style.css'

const nodeItems = [
  { type: 'condition', label: '条件节点', icon: <FilterOutlined />, color: '#1890ff' },
  { type: 'result', label: '结果节点', icon: <CheckCircleOutlined />, color: '#faad14' },
  { type: 'and', label: 'AND节点', icon: <ApartmentOutlined />, color: '#722ed1' },
  { type: 'or', label: 'OR节点', icon: <NodeIndexOutlined />, color: '#722ed1' },
]

export default function ToolBar() {
  const onDragStart = (event, nodeType) => {
    event.dataTransfer.setData('application/reactflow', nodeType)
    event.dataTransfer.effectAllowed = 'move'
  }

  return (
    <div className="toolbar">
      <div className="toolbar-title">节点工具箱</div>
      <div className="toolbar-items">
        {nodeItems.map((item) => (
          <div
            key={item.type}
            className="toolbar-item"
            draggable
            onDragStart={(e) => onDragStart(e, item.type)}
            style={{ borderLeft: `3px solid ${item.color}` }}
          >
            <span className="toolbar-icon" style={{ color: item.color }}>
              {item.icon}
            </span>
            <span>{item.label}</span>
          </div>
        ))}
      </div>
      <div className="toolbar-hint">
        拖拽节点到画布
      </div>
    </div>
  )
}
