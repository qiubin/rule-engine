import React, { useState, useEffect } from 'react'
import { Layout, Table, Button, Modal, Form, Input, Select, Tag, message, Popconfirm, Card } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import axios from 'axios'

const { Sider, Content } = Layout
const DE_API = '/api/v1/data-elements'
const DS_API = '/api/v1/data-sets'
const DICT_API = '/api/v1/dictionaries'

export default function DataElementMgr() {
  const [dataSets, setDataSets] = useState([])
  const [dataElements, setDataElements] = useState([])
  const [dictionaries, setDictionaries] = useState([])
  const [selectedDsId, setSelectedDsId] = useState(null)
  const [loading, setLoading] = useState(false)

  const [dsModalOpen, setDsModalOpen] = useState(false)
  const [editingDs, setEditingDs] = useState(null)
  const [dsForm] = Form.useForm()

  const [deModalOpen, setDeModalOpen] = useState(false)
  const [editingDe, setEditingDe] = useState(null)
  const [deForm] = Form.useForm()

  const fetchData = async () => {
    setLoading(true)
    try {
      const [dsRes, deRes, dictRes] = await Promise.all([
        axios.get(DS_API), axios.get(DE_API), axios.get(DICT_API)
      ])
      setDataSets(dsRes.data)
      setDataElements(deRes.data)
      setDictionaries(dictRes.data)
      if (dsRes.data.length > 0 && !selectedDsId) {
        setSelectedDsId(dsRes.data[0].id)
      }
    } catch (e) {
      message.error('加载失败')
    }
    setLoading(false)
  }

  useEffect(() => { fetchData() }, [])

  const filteredElements = selectedDsId
    ? dataElements.filter(de => de.datasetId === selectedDsId)
    : dataElements

  // 数据集操作
  const handleSaveDs = async () => {
    const values = await dsForm.validateFields()
    try {
      if (editingDs) {
        await axios.put(`${DS_API}/${editingDs.id}`, { ...editingDs, ...values })
      } else {
        await axios.post(DS_API, values)
      }
      message.success('保存成功')
      setDsModalOpen(false)
      fetchData()
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteDs = async (id) => {
    try {
      await axios.delete(`${DS_API}/${id}`)
      message.success('删除成功')
      if (selectedDsId === id) setSelectedDsId(null)
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  // 数据元操作
  const handleSaveDe = async () => {
    const values = await deForm.validateFields()
    try {
      const payload = { ...values, datasetId: selectedDsId }
      if (editingDe) {
        await axios.put(`${DE_API}/${editingDe.id}`, { ...editingDe, ...payload })
      } else {
        await axios.post(DE_API, payload)
      }
      message.success('保存成功')
      setDeModalOpen(false)
      fetchData()
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteDe = async (id) => {
    try {
      await axios.delete(`${DE_API}/${id}`)
      message.success('删除成功')
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  const dsColumns = [
    { title: 'ID', dataIndex: 'id', width: 50 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    {
      title: '操作', width: 120,
      render: (_, record) => (
        <>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => {
            setEditingDs(record)
            dsForm.setFieldsValue(record)
            setDsModalOpen(true)
          }} />
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteDs(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </>
      )
    }
  ]

  const deColumns = [
    { title: 'ID', dataIndex: 'id', width: 50 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '数据类型', dataIndex: 'dataType', width: 100, render: (v) => (
      <Tag color={v === 'NUMERIC' ? 'blue' : v === 'STRING' ? 'green' : 'orange'}>{v}</Tag>
    )},
    { title: '字典', dataIndex: 'dictCode', width: 120 },
    {
      title: '操作', width: 120,
      render: (_, record) => (
        <>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => {
            setEditingDe(record)
            deForm.setFieldsValue(record)
            setDeModalOpen(true)
          }}>编辑</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteDe(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </>
      )
    }
  ]

  return (
    <div style={{ padding: 24 }}>
      <h2>数据集与数据元管理</h2>

      {/* 数据集管理 */}
      <Card title="数据集" size="small" style={{ marginBottom: 16 }}
        extra={
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => {
            setEditingDs(null)
            dsForm.resetFields()
            setDsModalOpen(true)
          }}>新增数据集</Button>
        }
      >
        <Table rowKey="id" size="small" columns={dsColumns} dataSource={dataSets} pagination={false} bordered />
      </Card>

      {/* 数据元管理 */}
      <Card title="数据元" size="small"
        extra={
          <div style={{ display: 'flex', gap: 8 }}>
            <Select
              placeholder="选择数据集筛选"
              style={{ width: 200 }}
              value={selectedDsId}
              onChange={setSelectedDsId}
              allowClear
            >
              {dataSets.map(ds => <Select.Option key={ds.id} value={ds.id}>{ds.name}</Select.Option>)}
            </Select>
            <Button type="primary" size="small" icon={<PlusOutlined />} disabled={!selectedDsId} onClick={() => {
              setEditingDe(null)
              deForm.resetFields()
              setDeModalOpen(true)
            }}>新增数据元</Button>
          </div>
        }
      >
        <Table rowKey="id" size="small" columns={deColumns} dataSource={filteredElements} loading={loading} bordered />
      </Card>

      {/* 数据集弹窗 */}
      <Modal title={editingDs ? '编辑数据集' : '新增数据集'} open={dsModalOpen} onOk={handleSaveDs} onCancel={() => setDsModalOpen(false)}>
        <Form form={dsForm} layout="vertical">
          <Form.Item name="code" label="编码" rules={[{ required: true }]}>
            <Input disabled={!!editingDs} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 数据元弹窗 */}
      <Modal title={editingDe ? '编辑数据元' : '新增数据元'} open={deModalOpen} onOk={handleSaveDe} onCancel={() => setDeModalOpen(false)}>
        <Form form={deForm} layout="vertical">
          <Form.Item name="code" label="编码" rules={[{ required: true }]}>
            <Input disabled={!!editingDe} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="dataType" label="数据类型" rules={[{ required: true }]}>
            <Select placeholder="选择数据类型">
              <Select.Option value="STRING">字符串</Select.Option>
              <Select.Option value="NUMERIC">数值</Select.Option>
              <Select.Option value="DICTIONARY">字典</Select.Option>
              <Select.Option value="DICTIONARY_SET">字典集合</Select.Option>
              <Select.Option value="BOOLEAN">布尔</Select.Option>
              <Select.Option value="DATE">日期</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="dictCode" label="关联字典">
            <Select placeholder="选择字典" allowClear>
              {dictionaries.map(d => <Select.Option key={d.code} value={d.code}>{d.name}</Select.Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
