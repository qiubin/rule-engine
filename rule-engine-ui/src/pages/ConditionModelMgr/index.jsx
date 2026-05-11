import React, { useState, useEffect, useMemo, useCallback } from 'react'
import { Layout, Table, Button, Modal, Form, Input, Select, Tag, message, Popconfirm, Card, Tree } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SyncOutlined, FolderOutlined, FileOutlined } from '@ant-design/icons'
import axios from 'axios'

const { Sider, Content } = Layout
const CAT_API = '/api/v1/condition-model-categories'
const DE_API = '/api/v1/data-elements'
const DS_API = '/api/v1/data-sets'
const CM_API = '/api/v1/condition-models'

const TYPE_OPERATORS = {
  'NUMERIC': ['==', '!=', '>', '<'],
  'STRING': ['==', '!=', 'contains', 'regex_match'],
  'DICTIONARY': ['==', '!=', 'IN_SET'],
  'DICTIONARY_SET': ['IN_SET'],
  'DATE_TIME': ['==', '!=', '>', '<'],
  'SCRIPT': ['==']
}

function buildConditionTree(conditions, dataElements, dataSets, categories) {
  // ========== 数据集视图 ==========
  const dsRoot = []
  const l1Map = new Map()

  dataSets.forEach(ds => {
    const l1Code = ds.catL1Code || ''
    const l1Name = ds.catL1Name || '未分类'
    const l2Code = ds.catL2Code || ''
    const l2Name = ds.catL2Name || '未分类'
    const l3Code = ds.catL3Code || ''
    const l3Name = ds.catL3Name || '未分类'

    const hasCategory = l1Code || l2Code || l3Code

    if (!hasCategory) {
      dsRoot.push({
        title: `${ds.name} (${ds.code})`,
        key: `ds-${ds.id}`,
        isLeaf: true,
        datasetId: ds.id,
        icon: <FileOutlined />,
      })
      return
    }

    const l1Key = `dsl1-${l1Code}`
    let l1Node = l1Map.get(l1Key)
    if (!l1Node) {
      l1Node = { title: l1Name, key: l1Key, icon: <FolderOutlined />, children: [] }
      dsRoot.push(l1Node)
      l1Map.set(l1Key, l1Node)
    }

    const l2Key = `${l1Key}--l2-${l2Code || '_none'}`
    let l2Node = l1Node.children.find(n => n.key === l2Key)
    if (!l2Node) {
      l2Node = { title: l2Name, key: l2Key, icon: <FolderOutlined />, children: [] }
      l1Node.children.push(l2Node)
    }

    const l3Key = `${l2Key}--l3-${l3Code || '_none'}`
    let l3Node = l2Node.children.find(n => n.key === l3Key)
    if (!l3Node) {
      l3Node = { title: l3Name, key: l3Key, icon: <FolderOutlined />, children: [] }
      l2Node.children.push(l3Node)
    }

    l3Node.children.push({
      title: ds.name,
      key: `ds-${ds.id}`,
      isLeaf: true,
      datasetId: ds.id,
      icon: <FileOutlined />,
    })
  })

  const collapseRedundant = (nodes) => {
    const result = []
    for (const node of nodes) {
      if (node.isLeaf) {
        result.push(node)
        continue
      }
      const children = collapseRedundant(node.children)
      if (children.length === 1 && children[0].isLeaf && children[0].title === node.title) {
        result.push(children[0])
      } else {
        result.push({ ...node, children })
      }
    }
    return result
  }

  const collapsedDsRoot = collapseRedundant(dsRoot)

  const sortNodes = (nodes) => {
    nodes.sort((a, b) => (a.title || '').localeCompare(b.title || ''))
    nodes.forEach(n => { if (n.children) sortNodes(n.children) })
  }
  sortNodes(collapsedDsRoot)

  // ========== 条件分类视图 ==========
  const childrenMap = new Map()
  for (const cat of categories) {
    const pId = cat.parentId == null ? 'root' : cat.parentId
    if (!childrenMap.has(pId)) childrenMap.set(pId, [])
    childrenMap.get(pId).push(cat)
  }

  const buildCat = (parentId) => {
    const pId = parentId == null ? 'root' : parentId
    const nodes = []
    const cats = childrenMap.get(pId) || []
    cats.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || (a.name || '').localeCompare(b.name || ''))

    for (const cat of cats) {
      if (cat.code === 'RESULT_CONDITION') continue; // 过滤掉结果类型，专由结果管理页面处理
      const children = buildCat(cat.id)
      nodes.push({
        title: cat.name,
        key: `cat-${cat.id}`,
        categoryId: cat.id,
        icon: <FolderOutlined />,
        children,
      })
    }
    return nodes
  }

  const catRoot = buildCat(null)

  // ========== 合并为混合树 ==========
  const root = []
  if (collapsedDsRoot.length > 0) {
    root.push({ title: '数据集分类', key: 'view-ds', icon: <FolderOutlined />, children: collapsedDsRoot })
  }
  if (catRoot.length > 0) {
    root.push({ title: '条件分类', key: 'view-cat', icon: <FolderOutlined />, children: catRoot })
  }
  return root
}

export default function ConditionModelMgr() {
  const [conditions, setConditions] = useState([])
  const [categories, setCategories] = useState([])
  const [dataElements, setDataElements] = useState([])
  const [dataSets, setDataSets] = useState([])
  const [selectedTreeKey, setSelectedTreeKey] = useState(null)
  const [selectedCmIds, setSelectedCmIds] = useState([])
  const [loading, setLoading] = useState(false)

  const [catModalOpen, setCatModalOpen] = useState(false)
  const [editingCat, setEditingCat] = useState(null)
  const [catForm] = Form.useForm()

  const [cmModalOpen, setCmModalOpen] = useState(false)
  const [editingCm, setEditingCm] = useState(null)
  const [cmForm] = Form.useForm()

  const treeData = useMemo(() => buildConditionTree(conditions, dataElements, dataSets, categories), [conditions, dataElements, dataSets, categories])

  const nodeMap = useMemo(() => {
    const map = new Map()
    const walk = (nodes) => {
      for (const node of nodes) {
        map.set(node.key, node)
        if (node.children) walk(node.children)
      }
    }
    walk(treeData)
    return map
  }, [treeData])

  const catCodeToId = useMemo(() => {
    const map = new Map()
    for (const cat of categories) {
      if (cat.code && cat.code.startsWith('CAT_')) {
        map.set(cat.code.replace('CAT_', ''), cat.id)
      }
    }
    return map
  }, [categories])

  const selectedCatId = useMemo(() => {
    if (!selectedTreeKey) return null
    if (selectedTreeKey.startsWith('cat-')) {
      return Number(selectedTreeKey.replace('cat-', '')) || null
    }
    if (selectedTreeKey.startsWith('dsl1-')) {
      const catL1Code = selectedTreeKey.replace('dsl1-', '').split('--')[0]
      return catCodeToId.get(catL1Code) || null
    }
    if (selectedTreeKey.startsWith('cm-')) {
      const cmId = Number(selectedTreeKey.replace('cm-', ''))
      const cm = conditions.find(c => c.id === cmId)
      return cm?.categoryId || null
    }
    return null
  }, [selectedTreeKey, conditions, catCodeToId])

  const selectedCmInfo = useMemo(() => {
    if (!selectedTreeKey || !selectedTreeKey.startsWith('cm-')) return null
    const cmId = Number(selectedTreeKey.replace('cm-', ''))
    return conditions.find(c => c.id === cmId) || null
  }, [selectedTreeKey, conditions])

  const selectedL1Info = useMemo(() => {
    if (!selectedTreeKey) return null
    if (selectedTreeKey.startsWith('cat-')) {
      const catId = Number(selectedTreeKey.replace('cat-', ''))
      return categories.find(c => c.id === catId) || null
    }
    if (selectedTreeKey.startsWith('dsl1-')) {
      const catL1Code = selectedTreeKey.replace('dsl1-', '').split('--')[0]
      const catId = catCodeToId.get(catL1Code)
      return catId ? categories.find(c => c.id === catId) || null : null
    }
    return null
  }, [selectedTreeKey, categories, catCodeToId])

  const handleTreeSelect = useCallback((selectedKeys) => {
    const key = selectedKeys[0]
    setSelectedTreeKey(key || null)
    if (!key) {
      setSelectedCmIds([])
      return
    }
    if (key.startsWith('cm-')) {
      const cmId = Number(key.replace('cm-', ''))
      setSelectedCmIds([cmId])
    } else {
      const node = nodeMap.get(key)
      if (node) {
        const cIds = []
        const dsIds = []
        const catIds = []
        const walk = (n) => {
          if (n.isLeaf && n.conditionId) cIds.push(n.conditionId)
          if (n.isLeaf && n.datasetId) dsIds.push(n.datasetId)
          if (n.categoryId) catIds.push(n.categoryId)
          if (n.children) n.children.forEach(walk)
        }
        walk(node)

        let finalIds = [...cIds]
        if (dsIds.length > 0) {
          const deIds = dataElements.filter(de => dsIds.includes(de.datasetId)).map(de => de.id)
          const conditionIdsFromDs = conditions.filter(cm => deIds.includes(cm.dataElementId)).map(cm => cm.id)
          finalIds = [...finalIds, ...conditionIdsFromDs]
        }
        if (catIds.length > 0) {
          const conditionIdsFromCat = conditions.filter(cm => catIds.includes(cm.categoryId)).map(cm => cm.id)
          finalIds = [...finalIds, ...conditionIdsFromCat]
        }
        
        setSelectedCmIds(finalIds)
      } else {
        setSelectedCmIds([])
      }
    }
  }, [nodeMap, dataElements, conditions])

  const fetchData = async () => {
    setLoading(true)
    try {
      const [cmRes, catRes, deRes, dsRes] = await Promise.all([
        axios.get(CM_API), axios.get(CAT_API), axios.get(DE_API), axios.get(DS_API)
      ])
      setConditions(cmRes.data)
      setCategories(catRes.data)
      setDataElements(deRes.data)
      setDataSets(dsRes.data)
    } catch (e) {
      message.error('加载失败')
    }
    setLoading(false)
  }

  useEffect(() => { fetchData() }, [])

  const filteredConditions = selectedTreeKey
    ? conditions.filter(c => selectedCmIds.includes(c.id))
    : conditions

  const getDeName = (id) => dataElements.find(d => d.id === id)?.name || '-'
  const getCatName = (id) => categories.find(c => c.id === id)?.name || '-'

  // 分类操作
  const handleSaveCat = async () => {
    const values = await catForm.validateFields()
    try {
      if (editingCat) {
        await axios.put(`${CAT_API}/${editingCat.id}`, { ...editingCat, ...values })
      } else {
        await axios.post(CAT_API, values)
      }
      message.success('保存成功')
      setCatModalOpen(false)
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '保存失败')
    }
  }

  const handleDeleteCat = async (id) => {
    try {
      await axios.delete(`${CAT_API}/${id}`)
      message.success('删除成功')
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  // 条件操作
  const handleSaveCm = async () => {
    const values = await cmForm.validateFields()
    try {
      const payload = {
        ...values,
        categoryId: selectedCatId,
        operators: values.operators || [],
      }
      if (editingCm) {
        await axios.put(`${CM_API}/${editingCm.id}`, { ...editingCm, ...payload })
      } else {
        await axios.post(CM_API, payload)
      }
      message.success('保存成功')
      setCmModalOpen(false)
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '保存失败')
    }
  }

  const handleDeleteCm = async (id) => {
    try {
      await axios.delete(`${CM_API}/${id}`)
      message.success('删除成功')
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  const handleSync = async () => {
    try {
      const res = await axios.post(`${CM_API}/sync`)
      message.success(`同步完成：${res.data.categoryCount} 个分类，${res.data.conditionCount} 个条件`)
      setSelectedTreeKey(null)
      setSelectedCmIds([])
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '同步失败')
    }
  }

  const onDataElementChange = (deId) => {
    const de = dataElements.find(d => d.id === deId)
    if (de) {
      const defaultOps = TYPE_OPERATORS[de.dataType] || []
      cmForm.setFieldsValue({
        code: de.code,
        name: de.name,
        dataType: de.dataType,
        operators: defaultOps
      })
    }
  }

  const onNodeUsageChange = (usage) => {
    if (usage === 'RESULT') {
      cmForm.setFieldsValue({ dataElementId: undefined, code: undefined, name: undefined, dataType: undefined })
    }
  }

  const onDatasetChange = () => {
    cmForm.setFieldsValue({ dataElementId: undefined, code: undefined, name: undefined, dataType: undefined })
  }

  const cmColumns = [
    { title: 'ID', dataIndex: 'id', width: 50 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '分类', dataIndex: 'categoryId', render: (v) => getCatName(v) },
    { title: '数据元', dataIndex: 'dataElementId', render: (v) => getDeName(v) },
    { title: '数据类型', dataIndex: 'dataType', width: 90 },
    { title: '计算符', dataIndex: 'operators', render: (ops) => ops?.map(o => <Tag key={o}>{o}</Tag>) },
    { title: '用途', dataIndex: 'nodeUsage', width: 80, render: (v) => (
      <Tag color={v === 'CONDITION' ? 'blue' : v === 'RESULT' ? 'orange' : 'green'}>
        {v === 'CONDITION' ? '条件' : v === 'RESULT' ? '结果' : '通用'}
      </Tag>
    )},
    {
      title: '操作', width: 120,
      render: (_, record) => (
        <>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => {
            setEditingCm(record)
            cmForm.setFieldsValue({
              ...record,
              operators: record.operators || [],
            })
            setCmModalOpen(true)
          }} />
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteCm(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </>
      )
    }
  ]

  return (
    <div style={{ padding: 24 }}>
      <h2>条件分类与条件管理</h2>

      <Layout style={{ minHeight: 500, background: '#fff' }}>
        {/* 左侧分类树 */}
        <Sider width={320} style={{ background: '#fafafa', borderRight: '1px solid #f0f0f0', padding: 12 }}>
          <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
            <Button
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              block
              onClick={() => {
                setEditingCat(null)
                catForm.resetFields()
                setCatModalOpen(true)
              }}
            >
              新增分类
            </Button>
            <Popconfirm
              title="确认同步?"
              description="同步将清空现有条件分类和条件，并从数据集/数据元重新构建。已有画布中的条件引用将失效，是否继续？"
              onConfirm={handleSync}
              okText="继续"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button danger size="small" icon={<SyncOutlined />} block>同步</Button>
            </Popconfirm>
          </div>
          <Tree
            treeData={treeData}
            selectedKeys={selectedTreeKey ? [selectedTreeKey] : []}
            onSelect={handleTreeSelect}
            showIcon
            defaultExpandAll
          />
        </Sider>

        {/* 右侧内容 */}
        <Content style={{ padding: '0 16px' }}>
          {/* 选中节点信息卡片 */}
          {selectedCmInfo ? (
            <Card
              size="small"
              style={{ marginBottom: 16 }}
              title={
                <span>
                  <FileOutlined style={{ marginRight: 8 }} />
                  条件: {selectedCmInfo.name}
                </span>
              }
            >
              <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
                <span><strong>编码:</strong> {selectedCmInfo.code}</span>
                <span><strong>分类:</strong> {getCatName(selectedCmInfo.categoryId)}</span>
                <span><strong>数据类型:</strong> {selectedCmInfo.dataType}</span>
                <span><strong>数据元:</strong> {getDeName(selectedCmInfo.dataElementId)}</span>
                <span><strong>用途:</strong> {selectedCmInfo.nodeUsage}</span>
              </div>
            </Card>
          ) : selectedL1Info ? (
            <Card
              size="small"
              style={{ marginBottom: 16 }}
              title={
                <span>
                  <FolderOutlined style={{ marginRight: 8 }} />
                  分类: {selectedL1Info.name}
                </span>
              }
              extra={
                <div style={{ display: 'flex', gap: 8 }}>
                  <Button size="small" icon={<EditOutlined />} onClick={() => {
                    setEditingCat(selectedL1Info)
                    catForm.setFieldsValue(selectedL1Info)
                    setCatModalOpen(true)
                  }}>编辑</Button>
                  <Popconfirm title="确认删除?" onConfirm={() => handleDeleteCat(selectedL1Info.id)}>
                    <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
                  </Popconfirm>
                </div>
              }
            >
              <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
                <span><strong>编码:</strong> {selectedL1Info.code}</span>
                <span><strong>描述:</strong> {selectedL1Info.description || '-'}</span>
                <span><strong>排序:</strong> {selectedL1Info.sortOrder}</span>
              </div>
            </Card>
          ) : selectedTreeKey ? (
            <Card size="small" style={{ marginBottom: 16 }}>
              <FolderOutlined style={{ marginRight: 8 }} />
              已选择分类节点，共包含 {selectedCmIds.length} 个条件
            </Card>
          ) : null}

          {/* 条件表格 */}
          <Card
            title="条件列表"
            size="small"
            extra={
              <Button type="primary" size="small" icon={<PlusOutlined />} disabled={!selectedCatId} onClick={() => {
                setEditingCm(null)
                cmForm.resetFields()
                setCmModalOpen(true)
              }}>新增条件</Button>
            }
          >
            <Table rowKey="id" size="small" columns={cmColumns} dataSource={filteredConditions} loading={loading} bordered />
          </Card>
        </Content>
      </Layout>

      {/* 分类弹窗 */}
      <Modal title={editingCat ? '编辑分类' : '新增分类'} open={catModalOpen} onOk={handleSaveCat} onCancel={() => setCatModalOpen(false)}>
        <Form form={catForm} layout="vertical">
          <Form.Item name="code" label="分类编码" rules={[{ required: true }]}>
            <Input disabled={!!editingCat} />
          </Form.Item>
          <Form.Item name="name" label="分类名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" min={0} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 条件弹窗 */}
      <Modal title={editingCm ? '编辑条件' : '新增条件'} open={cmModalOpen} onOk={handleSaveCm} onCancel={() => setCmModalOpen(false)} width={700}>
        {selectedCatId && (
          <div style={{ marginBottom: 16, padding: '8px 12px', background: '#e6f7ff', borderRadius: 4 }}>
            所属分类：<b>{getCatName(selectedCatId)}</b>
          </div>
        )}
        <Form form={cmForm} layout="vertical">
          <Form.Item name="nodeUsage" label="节点用途" rules={[{ required: true }]}>
            <Select placeholder="选择用途" onChange={onNodeUsageChange}>
              <Select.Option value="CONDITION">条件节点</Select.Option>
              <Select.Option value="RESULT">结果节点</Select.Option>
              <Select.Option value="BOTH">通用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="datasetId" label="选择数据集">
            <Select placeholder="先选择数据集" onChange={onDatasetChange} allowClear>
              {dataSets.map(ds => <Select.Option key={ds.id} value={ds.id}>{ds.name}</Select.Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="dataElementId" label="选择数据元" rules={[{ required: false }]}>
            <Select placeholder="选择数据元，自动填充编码和名称" onChange={onDataElementChange} allowClear>
              {dataElements
                .filter(de => {
                  const selectedDs = cmForm.getFieldValue('datasetId')
                  return !selectedDs || de.datasetId === selectedDs
                })
                .map(de => (
                  <Select.Option key={de.id} value={de.id}>{de.name} ({de.code})</Select.Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item name="code" label="条件编码"><Input placeholder="自动来自数据元编码，可手动输入" /></Form.Item>
          <Form.Item name="name" label="条件名称"><Input placeholder="自动来自数据元名称，可手动输入" /></Form.Item>
          <Form.Item name="dataType" label="数据类型"><Input disabled placeholder="自动来自数据元类型" /></Form.Item>
          <Form.Item name="operators" label="计算符" rules={[{ required: false }]}>
            <Select mode="multiple" placeholder="选择支持的计算符">
              <Select.Option value="==">等于</Select.Option>
              <Select.Option value="!=">不等于</Select.Option>
              <Select.Option value=">">大于</Select.Option>
              <Select.Option value="<">小于</Select.Option>
              <Select.Option value="contains">包含</Select.Option>
              <Select.Option value="regex_match">正则匹配</Select.Option>
              <Select.Option value="IN_SET">在集合中</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="valueSource" label="值来源" rules={[{ required: true }]}>
            <Select placeholder="选择值来源">
              <Select.Option value="PARAM">参数</Select.Option>
              <Select.Option value="SQL">SQL查询</Select.Option>
              <Select.Option value="ADAPTER">适配器</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
