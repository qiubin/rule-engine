import React, { useState, useEffect } from 'react'
import { Layout, Table, Button, Modal, Form, Input, Empty, Card } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, PartitionOutlined } from '@ant-design/icons'
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

  const ruleColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '版本', dataIndex: 'version', width: 80 },
    { title: '状态', dataIndex: 'status', width: 100 },
    {
      title: '操作', width: 200,
      render: (_, record) => (
        <span key={'ops-' + record.id}>
          <Button type="link" icon={<PartitionOutlined />} onClick={() => handleEditRule(record)}>编辑画布</Button>
          <Button type="link" danger icon={<DeleteOutlined />} onClick={() => {
            if (window.confirm('确认删除规则 ' + record.name + '?')) {
              handleDeleteRule(record.id)
            }
          }}>删除</Button>
        </span>
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
    </Layout>
  )
}
