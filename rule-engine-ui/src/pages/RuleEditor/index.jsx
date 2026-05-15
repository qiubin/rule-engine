import React, { useState, useCallback, useRef, useEffect } from 'react'
import ReactFlow, {
  ReactFlowProvider,
  addEdge,
  useNodesState,
  useEdgesState,
  Controls,
  Background,
  MiniMap,
  Panel,
} from 'reactflow'
import { Button, message, Modal, Input, Select, Form } from 'antd'
import { SaveOutlined, PlayCircleOutlined, PlusOutlined, DeleteOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { nodeTypes } from '../../components/Nodes'
import ConfigPanel from '../../components/ConfigPanel'
import ToolBar from '../../components/ToolBar'
import axios from 'axios'
import './style.css'

const API_BASE = '/api/v1'

let id = 1
const getId = () => `node_${id++}`

function FlowCanvas() {
  const reactFlowWrapper = useRef(null)
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [reactFlowInstance, setReactFlowInstance] = useState(null)
  const [selectedNode, setSelectedNode] = useState(null)
  const [isConfigOpen, setIsConfigOpen] = useState(false)
  const [ruleForm] = Form.useForm()
  const [saveModalOpen, setSaveModalOpen] = useState(false)
  const [currentRule, setCurrentRule] = useState(null)
  const [ruleTypes, setRuleTypes] = useState([])
  const [urlRuleTypeId, setUrlRuleTypeId] = useState(null)
  const [urlRuleId, setUrlRuleId] = useState(null)
  const [execModalOpen, setExecModalOpen] = useState(false)
  const [execForm] = Form.useForm()
  const [execFields, setExecFields] = useState([])
  const [executing, setExecuting] = useState(false)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const typeId = params.get('type')
    const rId = params.get('ruleId')
    setUrlRuleTypeId(typeId ? Number(typeId) : null)
    setUrlRuleId(rId ? Number(rId) : null)

    if (nodes.length === 0) {
      setNodes([
        { id: 'start', type: 'start', position: { x: 100, y: 200 }, data: { label: '开始' } },
        { id: 'end', type: 'end', position: { x: 800, y: 200 }, data: { label: '结束' } },
      ])
    }
    fetchRuleTypes()

    // 如果有 ruleId，加载已有规则
    if (rId) {
      loadRule(Number(rId))
    }
  }, [])

  const fetchRuleTypes = async () => {
    try {
      const res = await axios.get(`${API_BASE}/rule-types`)
      setRuleTypes(res.data)
    } catch (e) {}
  }

  const loadRule = async (ruleId) => {
    try {
      const res = await axios.get(`${API_BASE}/rules/${ruleId}`)
      const rule = res.data
      setCurrentRule(rule)
      let loadedNodes = []
      let loadedEdges = []
      if (rule.canvasData) {
        const canvas = JSON.parse(rule.canvasData)
        if (canvas.nodes) loadedNodes = canvas.nodes
        if (canvas.edges) loadedEdges = canvas.edges
      }
      // 标准化已有 start/end 节点的 id（如 start-1 → start），并同步更新 edges
      const normalizeNodeId = (oldId, newId) => {
        loadedNodes.forEach(n => {
          if (n.id === oldId) n.id = newId
        })
        loadedEdges.forEach(e => {
          if (e.source === oldId) e.source = newId
          if (e.target === oldId) e.target = newId
        })
      }
      loadedNodes.forEach(n => {
        if (n.type === 'start' && n.id !== 'start') {
          normalizeNodeId(n.id, 'start')
        }
        if (n.type === 'end' && n.id !== 'end') {
          normalizeNodeId(n.id, 'end')
        }
      })
      // 确保画布始终有开始和结束节点
      const hasStart = loadedNodes.some(n => n.id === 'start')
      const hasEnd = loadedNodes.some(n => n.id === 'end')
      if (!hasStart) {
        loadedNodes.unshift({ id: 'start', type: 'start', position: { x: 100, y: 200 }, data: { label: '开始' } })
      }
      if (!hasEnd) {
        loadedNodes.push({ id: 'end', type: 'end', position: { x: 800, y: 200 }, data: { label: '结束' } })
      }
      // 自动添加缺失的默认连线：start → 第一个条件节点，结果节点 → end
      const conditionNodes = loadedNodes.filter(n => n.type === 'condition')
      const resultNodes = loadedNodes.filter(n => n.type === 'result')
      if (conditionNodes.length > 0 && !loadedEdges.some(e => e.source === 'start')) {
        loadedEdges.push({
          id: 'e_start_first', source: 'start', target: conditionNodes[0].id,
          type: 'smoothstep', animated: true
        })
      }
      resultNodes.forEach((rn, idx) => {
        if (!loadedEdges.some(e => e.source === rn.id)) {
          loadedEdges.push({
            id: `e_result_${rn.id}_end`, source: rn.id, target: 'end',
            type: 'smoothstep', animated: true
          })
        }
      })
      setNodes(loadedNodes)
      setEdges(loadedEdges)
    } catch (e) {
      message.error('加载规则失败')
    }
  }

  const onConnect = useCallback(
    (params) => {
      // condition 节点的 false handle 自动标记为 "否"
      let label = params.label || ''
      const sourceNode = nodes.find(n => n.id === params.source)
      if (sourceNode && sourceNode.type === 'condition') {
        if (params.sourceHandle === 'false') {
          label = '否'
        } else if (params.sourceHandle === 'true' || !params.sourceHandle) {
          label = '是'
        }
      }
      setEdges((eds) => addEdge({ ...params, type: 'smoothstep', animated: true, label }, eds))
    },
    [setEdges, nodes]
  )

  const onDragOver = useCallback((event) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  const onDrop = useCallback(
    (event) => {
      event.preventDefault()
      const type = event.dataTransfer.getData('application/reactflow')
      if (typeof type === 'undefined' || !type) return
      const position = reactFlowInstance.screenToFlowPosition({ x: event.clientX, y: event.clientY })
      const newNode = { id: getId(), type, position, data: { label: getNodeLabel(type) } }
      setNodes((nds) => nds.concat(newNode))
    },
    [reactFlowInstance]
  )

  const getNodeLabel = (type) => {
    const labels = { start: '开始', condition: '条件', result: '结果', and: 'AND', or: 'OR', end: '结束' }
    return labels[type] || '节点'
  }

  const onNodeClick = useCallback((_, node) => {
    setSelectedNode(node)
    setIsConfigOpen(true)
  }, [])

  const onNodeConfigUpdate = (nodeId, newData) => {
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: { ...node.data, ...newData } } : node)))
    setIsConfigOpen(false)
    message.success('节点配置已更新')
  }

  const handleSaveCanvas = async () => {
    if (!currentRule) {
      setSaveModalOpen(true)
      return
    }
    await doSaveCanvas(currentRule.id)
  }

  const doSaveCanvas = async (ruleId) => {
    try {
      const canvasData = {
        nodes: nodes.map(n => ({ id: n.id, type: n.type, position: n.position, data: n.data })),
        edges: edges.map(e => ({
          id: e.id, source: e.source, target: e.target, label: e.label,
          sourceHandle: e.sourceHandle, targetHandle: e.targetHandle
        }))
      }
      await axios.put(`${API_BASE}/rules/${ruleId}/canvas`, canvasData)
      message.success('画布保存成功')
    } catch (err) {
      message.error('保存失败: ' + (err.response?.data?.message || err.message))
    }
  }

  const handleCreateRule = async (values) => {
    try {
      const res = await axios.post(`${API_BASE}/rules`, {
        code: values.code,
        name: values.name,
        ruleTypeId: values.ruleTypeId
      })
      setCurrentRule(res.data)
      setSaveModalOpen(false)
      message.success('规则创建成功')
      await doSaveCanvas(res.data.id)
    } catch (err) {
      message.error('创建失败: ' + (err.response?.data?.message || err.message))
    }
  }

  // 解析画布中的条件字段
  const parseConditionFields = () => {
    const fields = []
    const seen = new Set()
    nodes.forEach(node => {
      if (node.type === 'condition' && node.data?.conditionConfig) {
        const cfg = node.data.conditionConfig
        const field = cfg.field
        if (field && !seen.has(field)) {
          seen.add(field)
          fields.push({ field, dataType: cfg.dataType, label: node.data.label || field })
        }
        // fieldCompare 需要额外输入字段A和字段B
        if (cfg.operator === 'fieldCompare') {
          if (cfg.value && !seen.has(cfg.value)) {
            seen.add(cfg.value)
            fields.push({ field: cfg.value, dataType: cfg.dataType, label: cfg.value + ' (字段A)' })
          }
          if (cfg.extraValue1 && !seen.has(cfg.extraValue1)) {
            seen.add(cfg.extraValue1)
            fields.push({ field: cfg.extraValue1, dataType: cfg.dataType, label: cfg.extraValue1 + ' (字段B)' })
          }
        }
      }
    })
    return fields
  }

  const handleExecute = async () => {
    if (!currentRule) { message.warning('请先保存规则'); return }
    const fields = parseConditionFields()
    setExecFields(fields)
    const initialValues = {}
    fields.forEach(f => { initialValues[f.field] = '' })
    execForm.setFieldsValue(initialValues)
    setExecModalOpen(true)
  }

  const doExecute = async () => {
    const values = await execForm.validateFields().catch(() => null)
    if (!values) return
    setExecuting(true)
    try {
      // 先保存画布，确保后端拿到最新数据
      await doSaveCanvas(currentRule.id)
      // 调用 test-execute（无需发布）
      const res = await axios.post(`${API_BASE}/rules/${currentRule.id}/test-execute`, values)
      Modal.success({
        title: '规则执行结果',
        width: 600,
        content: (
          <div>
            <div style={{ marginBottom: 12 }}>
              <span style={{ fontWeight: 'bold' }}>触发规则数: </span>
              <span style={{ color: res.data.firedRules > 0 ? '#52c41a' : '#999', fontSize: 18 }}>{res.data.firedRules}</span>
              <span style={{ marginLeft: 24, fontWeight: 'bold' }}>是否匹配: </span>
              <span style={{ color: res.data.matched ? '#52c41a' : '#ff4d4f', fontSize: 18 }}>{res.data.matched ? '是' : '否'}</span>
            </div>
            {res.data.results && Array.isArray(res.data.results) && res.data.results.length > 0 && (
              <div style={{ marginBottom: 12 }}>
                <div style={{ fontWeight: 'bold', marginBottom: 4 }}>匹配详情:</div>
                {res.data.results.map((r, idx) => (
                  <div key={idx} style={{ background: '#f0f0f0', padding: 8, marginBottom: 4, borderRadius: 4, fontSize: 13 }}>
                    <div>节点: {r.nodeLabel}</div>
                    <div>结果类型: {r.resultType}</div>
                    <div>结果值: {r.resultValue}</div>
                  </div>
                ))}
              </div>
            )}
            <details>
              <summary style={{ cursor: 'pointer', color: '#1890ff' }}>原始响应</summary>
              <pre style={{ maxHeight: 300, overflow: 'auto', fontSize: 12, background: '#f6ffed', padding: 8, borderRadius: 4 }}>
                {JSON.stringify(res.data, null, 2)}
              </pre>
            </details>
          </div>
        )
      })
      setExecModalOpen(false)
    } catch (err) {
      message.error('执行失败: ' + (err.response?.data?.message || err.message))
    }
    setExecuting(false)
  }

  const handleDeleteNode = () => {
    if (!selectedNode) return
    if (selectedNode.type === 'start' || selectedNode.type === 'end') { message.warning('开始和结束节点不能删除'); return }
    setNodes((nds) => nds.filter((n) => n.id !== selectedNode.id))
    setEdges((eds) => eds.filter((e) => e.source !== selectedNode.id && e.target !== selectedNode.id))
    setSelectedNode(null)
    setIsConfigOpen(false)
  }

  const goBack = () => {
    window.location.href = '/?page=types'
  }

  return (
    <div style={{ display: 'flex', height: '100%' }}>
      <div style={{ width: 200, borderRight: '1px solid #ddd', background: '#fafafa' }}>
        <ToolBar />
      </div>
      <div style={{ flex: 1, position: 'relative' }} ref={reactFlowWrapper}>
        <ReactFlow
          nodes={nodes} edges={edges}
          onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
          onConnect={onConnect} onInit={setReactFlowInstance}
          onDrop={onDrop} onDragOver={onDragOver}
          onNodeClick={onNodeClick}
          nodeTypes={nodeTypes} fitView
        >
          <Controls /><MiniMap /><Background variant="dots" gap={12} size={1} />
          <Panel position="top-right">
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <Button icon={<ArrowLeftOutlined />} onClick={goBack}>返回</Button>
              {currentRule && <span style={{ background: '#f0f0f0', padding: '4px 12px', borderRadius: 4, fontSize: 13 }}>
                当前规则: {currentRule.name} ({currentRule.code})
              </span>}
              <Button type="primary" icon={<SaveOutlined />} onClick={handleSaveCanvas}>保存画布</Button>
              <Button icon={<PlayCircleOutlined />} onClick={handleExecute}>测试执行</Button>
              {selectedNode && (
                <Button danger icon={<DeleteOutlined />} onClick={handleDeleteNode}>删除节点</Button>
              )}
            </div>
          </Panel>
        </ReactFlow>
      </div>
      <ConfigPanel
        open={isConfigOpen} onClose={() => setIsConfigOpen(false)}
        node={selectedNode} onUpdate={onNodeConfigUpdate}
      />
      <Modal title="测试执行参数" open={execModalOpen} onCancel={() => setExecModalOpen(false)} onOk={doExecute} confirmLoading={executing}>
        <Form form={execForm} layout="vertical">
          {execFields.length === 0 ? (
            <div style={{ color: '#999', padding: 20, textAlign: 'center' }}>该画布中未配置条件节点，无需输入参数</div>
          ) : (
            execFields.map(f => (
              <Form.Item
                key={f.field}
                name={f.field}
                label={`${f.label} (${f.dataType || 'STRING'})`}
                rules={[{ required: true, message: `请输入 ${f.label}` }]}
              >
                <Input placeholder={`请输入 ${f.label}`} />
              </Form.Item>
            ))
          )}
        </Form>
      </Modal>

      <Modal title="创建规则" open={saveModalOpen} onCancel={() => setSaveModalOpen(false)} onOk={() => ruleForm.submit()}>
        <Form form={ruleForm} onFinish={handleCreateRule} layout="vertical"
          initialValues={{ ruleTypeId: urlRuleTypeId || undefined }}>
          <Form.Item name="code" label="规则编码" rules={[{ required: true }]}>
            <Input placeholder="如：AGE_CHECK_001" />
          </Form.Item>
          <Form.Item name="name" label="规则名称" rules={[{ required: true }]}>
            <Input placeholder="如：主诉持续时间" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input placeholder="实现方法关键词 + 对应计算符 + 配置要点" />
          </Form.Item>
          <Form.Item name="ruleTypeId" label="规则类型" rules={[{ required: true, message: '必须选择规则类型' }]}>
            <Select placeholder="选择规则类型">
              {ruleTypes.map(rt => (
                <Select.Option key={rt.id} value={rt.id}>{rt.name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default function RuleEditor() {
  return (
    <ReactFlowProvider>
      <FlowCanvas />
    </ReactFlowProvider>
  )
}
