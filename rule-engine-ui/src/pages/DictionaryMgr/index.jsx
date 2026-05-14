import React, { useState, useEffect } from 'react'
import { Table, Button, Modal, Form, Input, Tabs, message, Popconfirm, Tag, Space, Select } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons'
import axios from 'axios'

const API = '/api/v1/dictionaries'
const ITEM_API = '/api/v1/dictionary-items'

export default function DictionaryMgr() {
  const [dicts, setDicts] = useState([])
  const [items, setItems] = useState([])
  const [itemTotal, setItemTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [itemLoading, setItemLoading] = useState(false)
  const [dictModalOpen, setDictModalOpen] = useState(false)
  const [itemModalOpen, setItemModalOpen] = useState(false)
  const [editingDict, setEditingDict] = useState(null)
  const [editingItem, setEditingItem] = useState(null)
  const [selectedDict, setSelectedDict] = useState(null)
  const [activeTab, setActiveTab] = useState('dict')
  const [itemKeyword, setItemKeyword] = useState('')
  const [itemPagination, setItemPagination] = useState({ current: 1, pageSize: 20 })
  const [dictForm] = Form.useForm()
  const [itemForm] = Form.useForm()

  const fetchDicts = async () => {
    setLoading(true)
    try {
      const res = await axios.get(API)
      setDicts(res.data)
    } catch (e) { message.error('加载失败') }
    setLoading(false)
  }

  const fetchItems = async (dictCode, page = 1, pageSize = 20, keyword = '') => {
    if (!dictCode) return
    setItemLoading(true)
    try {
      const res = await axios.get(`${ITEM_API}/search`, {
        params: { dictCode, keyword, page: page - 1, size: pageSize }
      })
      setItems(res.data.content || [])
      setItemTotal(res.data.totalElements || 0)
    } catch (e) {
      message.error('加载字典项失败')
      setItems([])
      setItemTotal(0)
    }
    setItemLoading(false)
  }

  useEffect(() => { fetchDicts() }, [])

  useEffect(() => {
    if (selectedDict) {
      fetchItems(selectedDict.code, itemPagination.current, itemPagination.pageSize, itemKeyword)
    }
  }, [selectedDict, itemPagination, itemKeyword])

  const handleSaveDict = async () => {
    const values = await dictForm.validateFields()
    try {
      if (editingDict) {
        await axios.put(`${API}/${editingDict.id}`, { ...editingDict, ...values })
      } else {
        await axios.post(API, values)
      }
      message.success('保存成功')
      setDictModalOpen(false)
      fetchDicts()
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteDict = async (id) => {
    try {
      await axios.delete(`${API}/${id}`)
      message.success('删除成功')
      fetchDicts()
      if (selectedDict && selectedDict.id === id) {
        setSelectedDict(null)
        setItems([])
        setActiveTab('dict')
      }
    } catch (e) { message.error(e.response?.data?.message || '删除失败') }
  }

  const handleSaveItem = async () => {
    const values = await itemForm.validateFields()
    const payload = {
      ...values,
      dictCode: selectedDict.code,
      dictionaryId: selectedDict.id,
      status: values.status || 'ENABLED'
    }
    try {
      if (editingItem) {
        await axios.put(`${ITEM_API}/${editingItem.id}`, { ...editingItem, ...payload })
      } else {
        await axios.post(ITEM_API, payload)
      }
      message.success('保存成功')
      setItemModalOpen(false)
      fetchItems(selectedDict.code, itemPagination.current, itemPagination.pageSize, itemKeyword)
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteItem = async (id) => {
    try {
      await axios.delete(`${ITEM_API}/${id}`)
      message.success('删除成功')
      fetchItems(selectedDict.code, itemPagination.current, itemPagination.pageSize, itemKeyword)
    } catch (e) { message.error(e.response?.data?.message || '删除失败') }
  }

  const handleManageItems = (record) => {
    setSelectedDict(record)
    setItemKeyword('')
    setItemPagination({ current: 1, pageSize: 20 })
    setActiveTab('items')
  }

  const handleItemTableChange = (pagination) => {
    setItemPagination({ current: pagination.current, pageSize: pagination.pageSize })
  }

  const dictColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '操作', width: 240,
      render: (_, record) => (
        <>
          <Button type="link" icon={<EditOutlined />} onClick={() => {
            setEditingDict(record)
            dictForm.setFieldsValue(record)
            setDictModalOpen(true)
          }}>编辑</Button>
          <Button type="link" onClick={() => handleManageItems(record)}>管理项</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteDict(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </>
      )
    }
  ]

  const itemColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '项编码', dataIndex: 'itemCode' },
    { title: '项名称', dataIndex: 'itemName' },
    { title: '项值', dataIndex: 'itemValue' },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (v) => {
        const enabled = v === 'ENABLED' || v == null
        return (
          <Tag color={enabled ? 'green' : 'red'}>
            {enabled ? '启用' : '禁用'}
          </Tag>
        )
      }
    },
    {
      title: '操作', width: 150,
      render: (_, record) => (
        <>
          <Button type="link" icon={<EditOutlined />} onClick={() => {
            setEditingItem(record)
            itemForm.setFieldsValue(record)
            setItemModalOpen(true)
          }}>编辑</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteItem(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </>
      )
    }
  ]

  return (
    <div style={{ padding: 24 }}>
      <Tabs activeKey={activeTab} onChange={setActiveTab}>
        <Tabs.TabPane tab="字典管理" key="dict">
          <div style={{ marginBottom: 16 }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => {
              setEditingDict(null)
              dictForm.resetFields()
              setDictModalOpen(true)
            }}>新增字典</Button>
          </div>
          <Table rowKey="id" columns={dictColumns} dataSource={dicts} loading={loading} bordered />
        </Tabs.TabPane>
        <Tabs.TabPane tab={selectedDict ? `字典项: ${selectedDict.name} (${itemTotal}条)` : '字典项管理'} key="items" disabled={!selectedDict}>
          <Space style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => {
              setEditingItem(null)
              itemForm.resetFields()
              setItemModalOpen(true)
            }}>新增字典项</Button>
            <Input.Search
              placeholder="搜索编码或名称"
              allowClear
              value={itemKeyword}
              onChange={(e) => setItemKeyword(e.target.value)}
              onSearch={(v) => {
                setItemKeyword(v)
                setItemPagination({ current: 1, pageSize: itemPagination.pageSize })
              }}
              style={{ width: 280 }}
              prefix={<SearchOutlined />}
            />
          </Space>
          <Table
            rowKey="id"
            columns={itemColumns}
            dataSource={items}
            loading={itemLoading}
            bordered
            pagination={{
              current: itemPagination.current,
              pageSize: itemPagination.pageSize,
              total: itemTotal,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50', '100'],
              showTotal: (total) => `共 ${total} 条`,
            }}
            onChange={handleItemTableChange}
          />
        </Tabs.TabPane>
      </Tabs>

      <Modal title={editingDict ? '编辑字典' : '新增字典'} open={dictModalOpen} onOk={handleSaveDict} onCancel={() => setDictModalOpen(false)}>
        <Form form={dictForm} layout="vertical">
          <Form.Item name="code" label="字典编码" rules={[{ required: true }]}>
            <Input disabled={!!editingDict} />
          </Form.Item>
          <Form.Item name="name" label="字典名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={editingItem ? '编辑字典项' : '新增字典项'} open={itemModalOpen} onOk={handleSaveItem} onCancel={() => setItemModalOpen(false)}>
        <Form form={itemForm} layout="vertical">
          <Form.Item name="itemCode" label="项编码" rules={[{ required: true }]}>
            <Input disabled={!!editingItem} />
          </Form.Item>
          <Form.Item name="itemName" label="项名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="itemValue" label="项值">
            <Input />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="ENABLED">
            <Select placeholder="选择状态">
              <Select.Option value="ENABLED">启用</Select.Option>
              <Select.Option value="DISABLED">禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
