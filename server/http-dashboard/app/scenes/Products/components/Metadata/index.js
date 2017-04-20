import './styles.less';
import Item from './components/Item';
import ItemsList from './components/ItemsList';

import TextField from './components/TextField';
import NumberField from './components/NumberField';
import CostField from './components/CostField';
import TimeField from './components/TimeField';
import ShiftField from './components/ShiftField';
import CoordinatesField from './components/CoordinatesField';

const Metadata = {
  Item: Item,
  ItemsList: ItemsList,
  Fields: {
    TextField,
    NumberField,
    CostField,
    TimeField,
    ShiftField,
    CoordinatesField
  }
};

export default Metadata;
