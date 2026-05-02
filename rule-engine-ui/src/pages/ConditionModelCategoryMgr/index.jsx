import React, { useState, useEffect } from 'react'
import { Table, Button, Modal, Form, Input, InputNumber, message, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import axios from 'axios'

const API = '/api/v1/condition-model-categories'

export default function ConditionModelCategoryMgr() {
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form] = Form.useForm()

  const fetch = async () => {
    setLoading(true)
    try {
      const res = await axios.get(API)
      setData(res.data)
    } catch (e) {
      message.error('加载失败')
    }
    setLoading(false)
  }

  useEffect(() => { fetch() }, [])

  const handleSave = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await axios.put(`${API}/${editing.id}`, { ...editing, ...values })
      } else {
        await axios.post(API, values)
      }
      message.success('保存成功')
      setModalOpen(false)
      fetch()
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDelete = async (id) => {
    try {
      await axios.delete(`${API}/${id}`)
      message.success('删除成功')
      fetch()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    { title: '状态', dataIndex: 'status', width: 80 },
    {
      title: '操作', width: 150,
      render: (_, record) => (
        <>
          <Button type="link" icon={<EditOutlined />} onClick={() => {
            setEditing(record)
            form.setFieldsValue(record)
            setModalOpen(true)
          }}>编辑</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </>
      )
    }
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => {
          setEditing(null)
          form.resetFields()
          setModalOpen(true)
        }}>新增分类</Button>
      </div>
      <Table rowKey="id" columns={columns} dataSource={data} loading={loading} bordered />
      <Modal title={editing ? '编辑分类' : '新增分类'} open={modalOpen} onOk={handleSave} onCancel={() => setModalOpen(false)}>
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="分类编码" rules={[{ required: true }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item name="name" label="分类名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
