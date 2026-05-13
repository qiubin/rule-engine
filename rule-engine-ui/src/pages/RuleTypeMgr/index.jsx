import React, { useState, useEffect } from 'react'
import { Layout, Table, Button, Modal, Form, Input, Empty, Card, Drawer, Tag, Space, Descriptions, message } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, PartitionOutlined, HistoryOutlined, FileTextOutlined, EyeOutlined, RollbackOutlined } from '@ant-design/icons'
import FlowCanvasViewer from '../../components/Canvas/FlowCanvasViewer'
import axios from 'axios'

const { Sider, Content } = Layout
const RT_API = '/api/v1/rule-types'
const RULE_API = '/api/v1/rules'

export default function RuleTypeMgr() {
  const [ruleTypes, setRuleTypes] = useState([])
  const [rules, setRules] = useState([])
  const [selectedTypeId, setSelectedTypeId] = useState(null)
  const [selectedType, setSelectedType] = useState(null)
  const [loading, setLoading] = useState(false)
  const [typeModalOpen, setTypeModalOpen] = useState(false)
  const [editingType, setEditingType] = useState(null)
  const [typeForm] = Form.useForm()
  const [errorMsg, setErrorMsg] = useState('')

  // 执行日志
  const [logDrawerOpen, setLogDrawerOpen] = useState(false)
  const [logList, setLogList] = useState([])
  const [logLoading, setLogLoading] = useState(false)
  const [currentLogRule, setCurrentLogRule] = useState(null)
  const [logDetailModalOpen, setLogDetailModalOpen] = useState(false)
  const [currentLog, setCurrentLog] = useState(null)

  // 历史版本
  const [versionModalOpen, setVersionModalOpen] = useState(false)
  const [versionList, setVersionList] = useState([])
  const [versionLoading, setVersionLoading] = useState(false)
  const [currentVersionRule, setCurrentVersionRule] = useState(null)
  const [versionPreviewModalOpen, setVersionPreviewModalOpen] = useState(false)
  const [currentVersion, setCurrentVersion] = useState(null)

  const fetchRuleTypes = async () => {
    try {
      setErrorMsg('')
      const res = await axios.get(RT_API)
      const list = res.data || []
      setRuleTypes(list)
      if (list.length > 0 && !selectedTypeId) {
        const first = list[0]
        setSelectedTypeId(first.id)
        setSelectedType(first)
      }
    } catch (e) {
      setErrorMsg('加载规则类型失败: ' + (e.message || '未知错误'))
    }
  }

  const fetchRules = async (ruleTypeId) => {
    if (!ruleTypeId) return
    setLoading(true)
    try {
      const res = await axios.get(`${RULE_API}?ruleTypeId=${ruleTypeId}`)
      setRules(res.data || [])
    } catch (e) {
      setErrorMsg('加载规则失败')
    }
    setLoading(false)
  }

  useEffect(() => { fetchRuleTypes() }, [])
  useEffect(() => { fetchRules(selectedTypeId) }, [selectedTypeId])

  const handleSaveType = async () => {
    const values = await typeForm.validateFields()
    try {
      if (editingType) {
        await axios.put(`${RT_API}/${editingType.id}`, { ...editingType, ...values })
      } else {
        await axios.post(RT_API, values)
      }
      setTypeModalOpen(false)
      fetchRuleTypes()
    } catch (e) {
      setErrorMsg('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteType = async (id) => {
    try {
      await axios.delete(`${RT_API}/${id}`)
      if (selectedTypeId === id) {
        setSelectedTypeId(null)
        setSelectedType(null)
        setRules([])
      }
      fetchRuleTypes()
    } catch (e) {
      setErrorMsg('删除失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteRule = async (id) => {
    try {
      await axios.delete(`${RULE_API}/${id}`)
      fetchRules(selectedTypeId)
    } catch (e) {
      setErrorMsg('删除失败')
    }
  }

  const handleCreateRule = () => {
    if (!selectedTypeId) {
      setErrorMsg('请先选择规则类型')
      return
    }
    window.location.href = `/?type=${selectedTypeId}&page=editor`
  }

  const handleEditRule = (rule) => {
    window.location.href = `/?type=${rule.ruleTypeId}&ruleId=${rule.id}&page=editor`
  }

  // 执行日志
  const openLogDrawer = async (rule) => {
    setCurrentLogRule(rule)
    setLogDrawerOpen(true)
    setLogLoading(true)
    try {
      const res = await axios.get(`${RULE_API}/${rule.id}/logs`)
      setLogList(res.data || [])
    } catch (e) {
      setErrorMsg('加载执行日志失败')
    }
    setLogLoading(false)
  }

  const openLogDetail = (log) => {
    setCurrentLog(log)
    setLogDetailModalOpen(true)
  }

  const parseHitNodeIds = (log) => {
    try {
      return JSON.parse(log.hitNodeIds || '[]')
    } catch (e) {
      return []
    }
  }

  // 历史版本
  const openVersionModal = async (rule) => {
    setCurrentVersionRule(rule)
    setVersionModalOpen(true)
    setVersionLoading(true)
    try {
      const res = await axios.get(`${RULE_API}/${rule.id}/versions`)
      setVersionList(res.data || [])
    } catch (e) {
      setErrorMsg('加载历史版本失败')
    }
    setVersionLoading(false)
  }

  const openVersionPreview = (version) => {
    setCurrentVersion(version)
    setVersionPreviewModalOpen(true)
  }

  const handleRollback = async (version) => {
    if (!window.confirm(`确认回滚到版本 ${version.version}？回滚后规则将变为草稿状态，需要重新发布。`)) {
      return
    }
    try {
      await axios.post(`${RULE_API}/${currentVersionRule.id}/rollback/${version.id}`)
      message.success('回滚成功')
      setVersionModalOpen(false)
      fetchRules(selectedTypeId)
    } catch (e) {
      setErrorMsg('回滚失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const ruleColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '编码', dataIndex: 'code', width: 160 },
    { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
    { title: '备注', dataIndex: 'remark', width: 220, ellipsis: true, render: v => v || '-' },
    { title: '版本', dataIndex: 'version', width: 70 },
    { title: '状态', dataIndex: 'status', width: 80 },
    {
      title: '操作', width: 300,
      render: (_, record) => (
        <Space size={0} key={'ops-' + record.id}>
          <Button type="link" size="small" icon={<PartitionOutlined />} onClick={() => handleEditRule(record)}>编辑画布</Button>
          <Button type="link" size="small" icon={<FileTextOutlined />} onClick={() => openLogDrawer(record)}>日志</Button>
          <Button type="link" size="small" icon={<HistoryOutlined />} onClick={() => openVersionModal(record)}>历史</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => {
            if (window.confirm('确认删除规则 ' + record.name + '?')) {
              handleDeleteRule(record.id)
            }
          }}>删除</Button>
        </Space>
      )
    }
  ]

  return (
    <Layout style={{ height: '100%', background: '#fff' }}>
      <Sider width={260} style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: 'auto' }}>
        <div style={{ padding: 16, borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ margin: 0 }}>规则类型</h3>
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => {
            setEditingType(null)
            typeForm.resetFields()
            setTypeModalOpen(true)
          }} />
        </div>
        <div style={{ padding: 8 }}>
          {ruleTypes.map(rt => (
            <Card
              key={rt.id}
              size="small"
              style={{
                marginBottom: 8,
                cursor: 'pointer',
                border: selectedTypeId === rt.id ? '1px solid #1890ff' : '1px solid #f0f0f0',
                background: selectedTypeId === rt.id ? '#e6f7ff' : '#fff'
              }}
              onClick={() => {
                setSelectedTypeId(rt.id)
                setSelectedType(rt)
              }}
              bodyStyle={{ padding: '8px 12px' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <span style={{ fontWeight: 'bold', fontSize: 14 }}>{rt.name}</span>
                  <span style={{ fontSize: 12, color: '#999', marginLeft: 8 }}>{rt.code}</span>
                </div>
                <div style={{ display: 'flex', gap: 4 }} onClick={e => e.stopPropagation()}>
                  <Button type="text" size="small" icon={<EditOutlined />}
                    onClick={() => {
                      setEditingType(rt)
                      typeForm.setFieldsValue(rt)
                      setTypeModalOpen(true)
                    }}
                  />
                  <Button type="text" size="small" danger icon={<DeleteOutlined />}
                    onClick={() => {
                      if (window.confirm('确认删除规则类型 "' + rt.name + '"?\n（若该类型下存在规则将无法删除）')) {
                        handleDeleteType(rt.id)
                      }
                    }}
                  />
                </div>
              </div>
            </Card>
          ))}
        </div>
      </Sider>
      <Content style={{ padding: 24, background: '#fff', overflow: 'auto' }}>
        {errorMsg && (
          <div style={{ padding: '8px 16px', background: '#fff2f0', border: '1px solid #ffccc7', color: '#cf1322', marginBottom: 16, borderRadius: 4 }}>
            {errorMsg}
          </div>
        )}
        <div>
          {selectedType ? (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <div>
                  <h2 style={{ margin: 0 }}>{selectedType.name}</h2>
                  <div style={{ color: '#999', fontSize: 13 }}>{selectedType.description || '无描述'}</div>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <Button icon={<EditOutlined />} onClick={() => {
                    setEditingType(selectedType)
                    typeForm.setFieldsValue(selectedType)
                    setTypeModalOpen(true)
                  }}>编辑类型</Button>
                  <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateRule}>新建规则</Button>
                </div>
              </div>
              <Table rowKey="id" columns={ruleColumns} dataSource={rules} loading={loading} bordered />
            </div>
          ) : (
            <Empty description="请选择左侧规则类型" />
          )}
        </div>
      </Content>

      <Modal title={editingType ? '编辑规则类型' : '新增规则类型'} open={typeModalOpen} onOk={handleSaveType} onCancel={() => setTypeModalOpen(false)}>
        <Form form={typeForm} layout="vertical">
          <Form.Item name="code" label="类型编码" rules={[{ required: true }]}>
            <Input disabled={!!editingType} />
          </Form.Item>
          <Form.Item name="name" label="类型名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 执行日志 Drawer */}
      <Drawer
        title={`规则执行日志 - ${currentLogRule?.name || ''}`}
        width={720}
        open={logDrawerOpen}
        onClose={() => setLogDrawerOpen(false)}
      >
        <Table
          rowKey="id"
          loading={logLoading}
          dataSource={logList}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: '执行时间', dataIndex: 'executedAt', width: 160 },
            { title: '状态', render: (_, r) => (
              <Tag color={r.status === 'SUCCESS' ? 'green' : r.status === 'NO_HIT' ? 'orange' : 'red'}>
                {r.status === 'SUCCESS' ? '成功' : r.status === 'NO_HIT' ? '未命中' : '失败'}
              </Tag>
            ), width: 80 },
            { title: '命中数', dataIndex: 'firedCount', width: 70 },
            { title: '耗时(ms)', dataIndex: 'durationMs', width: 90 },
            { title: '操作', width: 90, render: (_, r) => (
              <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openLogDetail(r)}>查看</Button>
            ) }
          ]}
        />
      </Drawer>

      {/* 日志详情 Modal */}
      <Modal
        title="执行详情"
        width={900}
        open={logDetailModalOpen}
        onCancel={() => setLogDetailModalOpen(false)}
        footer={null}
      >
        {currentLog && (
          <div>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="规则编码">{currentLog.ruleCode}</Descriptions.Item>
              <Descriptions.Item label="规则版本">{currentLog.ruleVersion}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={currentLog.status === 'SUCCESS' ? 'green' : currentLog.status === 'NO_HIT' ? 'orange' : 'red'}>
                  {currentLog.status === 'SUCCESS' ? '成功' : currentLog.status === 'NO_HIT' ? '未命中' : '失败'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="耗时">{currentLog.durationMs} ms</Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12 }}>
              <div style={{ fontWeight: 'bold', marginBottom: 8 }}>输入参数</div>
              <pre style={{ background: '#f6ffed', padding: 8, borderRadius: 4, fontSize: 12, maxHeight: 200, overflow: 'auto' }}>
                {JSON.stringify(JSON.parse(currentLog.paramsJson || '{}'), null, 2)}
              </pre>
            </div>
            <div style={{ marginTop: 12 }}>
              <div style={{ fontWeight: 'bold', marginBottom: 8 }}>输出结果</div>
              <pre style={{ background: '#e6f7ff', padding: 8, borderRadius: 4, fontSize: 12, maxHeight: 200, overflow: 'auto' }}>
                {JSON.stringify(JSON.parse(currentLog.outputJson || '{}'), null, 2)}
              </pre>
            </div>
            <div style={{ marginTop: 12, height: 400, border: '1px solid #f0f0f0', borderRadius: 4 }}>
              <FlowCanvasViewer
                nodes={currentLogRule?.canvasData ? JSON.parse(currentLogRule.canvasData).nodes : []}
                edges={currentLogRule?.canvasData ? JSON.parse(currentLogRule.canvasData).edges : []}
                highlightedNodeIds={parseHitNodeIds(currentLog)}
              />
            </div>
          </div>
        )}
      </Modal>

      {/* 历史版本 Modal */}
      <Modal
        title={`历史版本 - ${currentVersionRule?.name || ''}`}
        open={versionModalOpen}
        onCancel={() => setVersionModalOpen(false)}
        footer={null}
        width={700}
      >
        <Table
          rowKey="id"
          loading={versionLoading}
          dataSource={versionList}
          pagination={false}
          columns={[
            { title: '版本号', dataIndex: 'version', width: 80 },
            { title: '保存时间', dataIndex: 'createdAt', width: 160 },
            { title: '备注', dataIndex: 'changeNote', render: v => v || '-' },
            { title: '操作', width: 160, render: (_, v) => (
              <Space>
                <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openVersionPreview(v)}>查看</Button>
                <Button type="link" size="small" icon={<RollbackOutlined />} onClick={() => handleRollback(v)}>回滚</Button>
              </Space>
            ) }
          ]}
        />
      </Modal>

      {/* 版本预览 Modal */}
      <Modal
        title={`版本预览 - 版本 ${currentVersion?.version || ''}`}
        open={versionPreviewModalOpen}
        onCancel={() => setVersionPreviewModalOpen(false)}
        footer={null}
        width={900}
      >
        <div style={{ height: 500, border: '1px solid #f0f0f0', borderRadius: 4 }}>
          <FlowCanvasViewer
            nodes={currentVersion?.canvasData ? JSON.parse(currentVersion.canvasData).nodes : []}
            edges={currentVersion?.canvasData ? JSON.parse(currentVersion.canvasData).edges : []}
          />
        </div>
      </Modal>
    </Layout>
  )
}
